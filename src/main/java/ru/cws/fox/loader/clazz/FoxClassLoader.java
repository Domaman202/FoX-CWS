package ru.cws.fox.loader.clazz;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinylog.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

public final class FoxClassLoader extends ClassLoader {
    private final @NotNull ClassLoader parentLoader;
    private final @NotNull DynamicClassLoader dynamicLoader;
    private final @NotNull FoxTransformer transformer;
    private Predicate<String> transformationFilter;

    public FoxClassLoader(@NotNull FoxTransformer transformer) {
        super(new DynamicClassLoader(new URL[0]));
        this.parentLoader = FoxClassLoader.class.getClassLoader();
        this.dynamicLoader = (DynamicClassLoader) this.getParent();
        this.transformer = transformer;
        this.transformationFilter = name -> Arrays.stream(new String[]{"java.", "javax.", "org.objectweb.asm."}).noneMatch(name::startsWith);
    }

    public void addTransformationPath(@NotNull Path path) {
        try {
            this.addTransformationPath(path.toUri().toURL());
        } catch (MalformedURLException e) {
            Logger.error(e, "Fail to add transformation path: {}", path);
        }
    }

    public void addTransformationPath(@NotNull URL path) {
        this.dynamicLoader.addURL(path);
    }

    public URL[] getTransformationPaths() {
        return this.dynamicLoader.getURLs();
    }

    public void addTransformationFilter(@NotNull Predicate<String> filter) {
        this.transformationFilter = this.transformationFilter.and(filter);
    }

    public boolean classIsLoaded(String name) {
        return this.findLoadedClass(name.replace('/', '.')) != null;
    }

    @Override
    protected @NotNull Class<?> loadClass(@NotNull String name, final boolean resolve) throws ClassNotFoundException {
        synchronized (this.getClassLoadingLock(name)) {
            String canonicalName = name.replace('/', '.');

            Class<?> loaded = this.findLoadedClass(canonicalName);
            if (loaded != null) {
                if (resolve) this.resolveClass(loaded);
                return loaded;
            }

            if (canonicalName.startsWith("java."))
                return this.parentLoader.loadClass(canonicalName);

            Class<?> transformed = this.findTransformedClass(canonicalName, TransformPhase.INITIALIZE);
            if (transformed != null)
                return transformed;

            return this.parentLoader.loadClass(canonicalName);
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String canonicalName = name.replace('/', '.');

        if (canonicalName.startsWith("java."))
            return null;

        Class<?> transformed = this.findTransformedClass(canonicalName, TransformPhase.INITIALIZE);
        if (transformed == null)
            throw new ClassNotFoundException(canonicalName);
        return transformed;
    }

    private @Nullable Class<?> findTransformedClass(@NotNull String name, @NotNull TransformPhase phase) {
        byte[] transformedBytes = this.transformClassData(name, phase);
        if (transformedBytes == null)
            return null;

        Class<?> alreadyLoaded = this.findLoadedClass(name);
        if (alreadyLoaded != null)
            return alreadyLoaded;

        // Maybe need to implement define package...

        return this.defineClass(name, transformedBytes, 0, transformedBytes.length);
    }

    public byte[] transformClassData(String name, @NotNull TransformPhase phase) {
        byte[] bytes = this.getClassData(name, phase);
        if (bytes == null)
            return null;
        if (!this.transformationFilter.test(name))
            return null;
        return this.transformer.transform(name, bytes, phase);
    }

    public byte[] getClassData(String name, @NotNull TransformPhase phase) {
        String resourceName = name.replace('.', '/') + ".class";

        URL url = this.findResource(resourceName);
        if (url == null)
            if (phase == TransformPhase.INITIALIZE)
                return null;
            else url = this.parentLoader.getResource(resourceName);
        if (url == null)
            return null;

        try {
            URLConnection connection = url.openConnection();
            try (InputStream stream = connection.getInputStream()) {
                int length = connection.getContentLength();
                byte[] bytes = new byte[length];

                int position = 0, remain = length, read;
                while((read = stream.read(bytes, position, remain)) != -1 && remain > 0) {
                    position += read;
                    remain -= read;
                }

                return bytes;
            }
        } catch (IOException e) {
            Logger.error(e, "Fail to get class data: {}", name);
            return null;
        }
    }

    @Override
    public @Nullable URL getResource(@NotNull String name) {
        URL url = this.dynamicLoader.getResource(name);
        if (url != null)
            return url;
        return this.parentLoader.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(@NotNull String name) throws IOException {
        Enumeration<URL> resources = this.dynamicLoader.getResources(name);
        if (resources.hasMoreElements())
            return resources;
        return this.parentLoader.getResources(name);
    }

    @Override
    protected URL findResource(String name) {
        return this.dynamicLoader.findResource(name);
    }

    @Override
    public @Nullable InputStream getResourceAsStream(String name) {
        InputStream stream = this.dynamicLoader.getResourceAsStream(name);
        if (stream != null)
            return stream;
        return this.parentLoader.getResourceAsStream(name);
    }

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private static final class DummyClassLoader extends ClassLoader {
        private static final Enumeration<URL> EMPTY_ENUMERATION = new Enumeration<>() {
            @Override
            public boolean hasMoreElements() {
                return false;
            }

            @Override
            public @NotNull URL nextElement() {
                throw new NoSuchElementException();
            }
        };

        @Override
        protected @NotNull Class<?> loadClass(final @NotNull String name, final boolean resolve) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }

        @Override
        public @Nullable URL getResource(final @NotNull String name) {
            return null;
        }

        @Override
        public @NotNull Enumeration<URL> getResources(final @NotNull String name) throws IOException {
            return EMPTY_ENUMERATION;
        }

        static {
            ClassLoader.registerAsParallelCapable();
        }
    }

    private static final class DynamicClassLoader extends URLClassLoader {
        public DynamicClassLoader(@NotNull URL @NotNull [] urls) {
            super(urls, new DummyClassLoader());
        }

        @Override
        public void addURL(@NotNull URL url) {
            super.addURL(url);
        }

        static {
            ClassLoader.registerAsParallelCapable();
        }
    }
}
