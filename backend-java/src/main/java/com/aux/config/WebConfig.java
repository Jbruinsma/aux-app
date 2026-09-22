package com.aux.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.aux.auth.CurrentUserResolver;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final Path uploadsDir;
    private final CurrentUserResolver currentUserResolver;

    public WebConfig(@Value("${aux.uploads-dir}") String uploadsDir, CurrentUserResolver currentUserResolver) throws Exception {
        this.currentUserResolver = currentUserResolver;
        this.uploadsDir = Path.of(uploadsDir).toAbsolutePath().normalize();
        for (String sub : new String[] {"covers", "mp3s", "pfps"}) {
            Files.createDirectories(this.uploadsDir.resolve(sub));
        }
    }

    // Same as FastAPI's CORSMiddleware(allow_origins=["*"], ...)
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedOriginPatterns("*").allowedMethods("*").allowCredentials(true);
    }

    // Replaces the three /uploads/{covers,mp3s,pfps}/{filename} routes in app.py
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**").addResourceLocations(uploadsDir.toUri().toString());
    }

    // Lets controllers take `@CurrentUser UserEntity user`
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserResolver);
    }
}
