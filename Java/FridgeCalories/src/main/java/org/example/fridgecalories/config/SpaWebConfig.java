package org.example.fridgecalories.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the compiled Angular app bundled into the jar, so the whole product
 * ships as a single deployable on one URL.
 *
 * <p>Angular owns its own client-side routes ({@code /inventory}, {@code /shop}, ...).
 * Those paths don't exist as files on the server, so a plain refresh or a shared
 * link would 404 without this fallback: anything that isn't a real static file
 * and isn't an API call gets the SPA entry point, and Angular's router takes over.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // API calls must keep their real status codes instead of
                        // silently returning the HTML shell.
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
