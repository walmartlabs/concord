package com.walmartlabs.concord.process.loader;

import com.walmartlabs.concord.imports.ImportsListener;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static com.walmartlabs.concord.process.loader.ProjectLoaderUtils.getRuntimeType;
import static com.walmartlabs.concord.process.loader.StandardRuntimeTypes.CONCORD_V1_RUNTIME_TYPE;
import static java.util.Objects.requireNonNull;

/**
 * A project loader that tries to auto-detect the type of the runtime in ${workDir}
 * and use the appropriate runtime-specific loader.
 */
public class DelegatingProjectLoader implements ProjectLoader {

    private final Set<ProjectLoader> delegates;

    @Inject
    public DelegatingProjectLoader(Set<ProjectLoader> delegates) {
        this.delegates = requireNonNull(delegates);
    }

    @Override
    public boolean supports(String runtime) {
        for (var delegate : delegates) {
            if (delegate.supports(runtime)) {
                return true;
            }
        }
        return false;
    }

    public Result loadProject(Path workDir, ImportsNormalizer importsNormalizer, ImportsListener listener) throws Exception {
        var runtime = getRuntimeType(workDir).orElse(CONCORD_V1_RUNTIME_TYPE);
        return loadProject(workDir, runtime, importsNormalizer, listener);
    }

    @Override
    public Result loadProject(Path workDir, String runtime, ImportsNormalizer importsNormalizer, ImportsListener listener) throws Exception {
        return getLoader(runtime).orElseThrow(() -> new UnsupportedRuntimeTypeException("Unsupported runtime type: " + runtime))
                .loadProject(workDir, runtime, importsNormalizer, listener);
    }

    private Optional<ProjectLoader> getLoader(String runtime) {
        for (var delegate : delegates) {
            if (delegate.supports(runtime)) {
                return Optional.of(delegate);
            }
        }
        return Optional.empty();
    }
}
