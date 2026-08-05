import { CommonModule } from '@angular/common';
import { Component, ElementRef, EventEmitter, OnDestroy, OnInit, Output, ViewChild } from '@angular/core';
import { BrowserMultiFormatReader, IScannerControls } from '@zxing/browser';

// The native BarcodeDetector API isn't in TypeScript's default DOM lib yet.
// Chrome/Edge on Android and desktop support it with an on-device ML model,
// which is far more tolerant of blur/glare/angle than the pure-JS ZXing reader.
interface DetectedBarcode {
  rawValue: string;
}

interface NativeBarcodeDetector {
  detect(source: CanvasImageSource): Promise<DetectedBarcode[]>;
}

interface BarcodeDetectorConstructor {
  new (options?: { formats?: string[] }): NativeBarcodeDetector;
}

declare const BarcodeDetector: BarcodeDetectorConstructor | undefined;

@Component({
  selector: 'app-barcode-scanner',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './barcode-scanner.component.html',
  styleUrl: './barcode-scanner.component.css',
})
export class BarcodeScannerComponent implements OnInit, OnDestroy {
  @Output() scanned = new EventEmitter<string>();
  @Output() closed = new EventEmitter<void>();
  @ViewChild('video', { static: true }) videoElement!: ElementRef<HTMLVideoElement>;

  error: string | null = null;
  status = 'Iniciando...';
  framesScanned = 0;
  torchAvailable = false;
  torchOn = false;

  private reader = new BrowserMultiFormatReader();
  private controls?: IScannerControls;
  private videoTrack?: MediaStreamTrack;
  private stream?: MediaStream;
  private nativeLoopHandle?: number;
  private stopped = false;

  ngOnInit(): void {
    // Started from ngOnInit (not ngAfterViewInit): the ViewChild is `static: true`
    // so it's already resolved here, and mutating state in ngOnInit is safe —
    // doing it in ngAfterViewInit throws NG0100 (ExpressionChangedAfterItHasBeenCheckedError)
    // because the view has already been checked for this cycle by that point.
    this.start();
  }

  private async start(): Promise<void> {
    if (!window.isSecureContext) {
      this.error =
        'La cámara requiere una conexión segura (HTTPS o localhost). Esta página se ha cargado por HTTP normal, así que el navegador bloquea el acceso antes incluso de poder pedir permiso.';
      return;
    }

    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      this.error =
        'Este navegador no expone navigator.mediaDevices.getUserMedia en esta página (falta soporte o está bloqueado).';
      return;
    }

    try {
      this.status = 'Pidiendo acceso a la cámara (revisa si te sale un aviso del navegador)...';
      this.stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: { ideal: 'environment' },
          width: { ideal: 1920 },
          height: { ideal: 1080 },
        },
        audio: false,
      });
      await this.tryEnableContinuousFocus(this.stream);
      this.videoTrack = this.stream.getVideoTracks()[0];
      const capabilities = this.videoTrack?.getCapabilities?.() as (MediaTrackCapabilities & { torch?: boolean }) | undefined;
      this.torchAvailable = !!capabilities?.torch;

      this.status = `Cámara concedida (${this.videoTrack?.label || 'sin nombre'}). Conectando vídeo...`;

      if (typeof BarcodeDetector !== 'undefined') {
        await this.startNativeDetection(this.stream);
      } else {
        await this.startZxingDetection(this.stream);
      }
    } catch (err) {
      this.error = this.describeError(err);
    }
  }

  private async startNativeDetection(stream: MediaStream): Promise<void> {
    const video = this.videoElement.nativeElement;
    video.srcObject = stream;
    await video.play();

    const detector = new BarcodeDetector!({
      formats: ['ean_13', 'ean_8', 'upc_a', 'upc_e', 'code_128', 'code_39', 'qr_code'],
    });

    const loop = async () => {
      if (this.stopped) {
        return;
      }
      this.framesScanned++;
      try {
        const results = await detector.detect(video);
        if (results.length > 0) {
          this.scanned.emit(results[0].rawValue);
          this.stop();
          return;
        }
      } catch {
        // Per-frame detection failures are expected while nothing is in view — keep looping.
      }
      this.status = `Escaneando (motor nativo)... (${this.framesScanned} fotogramas analizados, ninguno reconocido todavía)`;
      this.nativeLoopHandle = requestAnimationFrame(loop);
    };
    this.nativeLoopHandle = requestAnimationFrame(loop);
  }

  private async startZxingDetection(stream: MediaStream): Promise<void> {
    this.controls = await this.reader.decodeFromStream(
      stream,
      this.videoElement.nativeElement,
      (result, err) => {
        this.framesScanned++;
        if (result) {
          this.scanned.emit(result.getText());
          this.stop();
          return;
        }
        // NotFoundException fires on every frame with no barcode in view — expected, ignore it.
        // Anything else is worth surfacing since it means decoding itself is failing.
        if (err && err.name !== 'NotFoundException' && err.name !== 'NotFoundException2') {
          this.status = `Escaneando... (${this.framesScanned} fotogramas, último error: ${err.name})`;
          return;
        }
        this.status = `Escaneando... (${this.framesScanned} fotogramas analizados, ninguno reconocido todavía)`;
      },
    );
  }

  private async tryEnableContinuousFocus(stream: MediaStream): Promise<void> {
    const [track] = stream.getVideoTracks();
    if (!track?.getCapabilities) {
      return;
    }
    const capabilities = track.getCapabilities() as MediaTrackCapabilities & { focusMode?: string[] };
    if (!capabilities.focusMode?.includes('continuous')) {
      return;
    }
    try {
      await track.applyConstraints({ advanced: [{ focusMode: 'continuous' } as MediaTrackConstraintSet] });
    } catch {
      // Not all devices honor this — safe to ignore, we just fall back to whatever focus it already has.
    }
  }

  private describeError(err: unknown): string {
    const name = err instanceof DOMException ? err.name : '';
    const message = err instanceof Error ? err.message : String(err);
    const base = (() => {
      switch (name) {
        case 'NotAllowedError':
          return 'Se ha denegado el permiso de cámara. Revisa los permisos de este sitio en tu navegador (o del navegador en los ajustes del sistema) e inténtalo de nuevo.';
        case 'NotFoundError':
          return 'No se ha encontrado ninguna cámara en este dispositivo.';
        case 'NotReadableError':
          return 'La cámara ya está siendo usada por otra aplicación.';
        default:
          return 'No se pudo acceder a la cámara. Comprueba los permisos del navegador para este sitio.';
      }
    })();
    return `${base} (${name || 'sin tipo'}: ${message})`;
  }

  toggleTorch(): void {
    if (!this.videoTrack) {
      return;
    }
    this.torchOn = !this.torchOn;
    this.videoTrack.applyConstraints({ advanced: [{ torch: this.torchOn } as MediaTrackConstraintSet] }).catch(() => {
      this.torchOn = !this.torchOn;
    });
  }

  private stop(): void {
    this.stopped = true;
    if (this.nativeLoopHandle != null) {
      cancelAnimationFrame(this.nativeLoopHandle);
      this.nativeLoopHandle = undefined;
    }
    this.controls?.stop();
    this.stream?.getTracks().forEach((track) => track.stop());
  }

  close(): void {
    this.stop();
    this.closed.emit();
  }

  ngOnDestroy(): void {
    this.stop();
  }
}
