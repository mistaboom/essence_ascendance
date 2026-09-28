package dev.essence.packtester;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

final class PackService {
    interface Builder { ProductionBuild.Artifact build(Path project, PrismDiscovery.Instance target, Cancellation cancel, Consumer<String> log) throws Exception; }
    interface Launcher {
        void prepare(PrismDiscovery.Instance target, Path executable, Consumer<String> log) throws Exception;
        void launch(PrismDiscovery.Instance target, Path executable, Consumer<String> log) throws Exception;
    }
    interface ClosedCheck { void check(PrismDiscovery.Instance target, boolean confirmed) throws Exception; }
    private final Path project;
    private final Builder builder;
    private final Deployment deployment;
    private final Launcher launcher;
    private final ClosedCheck closed;
    PackService(Path project) {
        this(project, new ProductionBuild()::build, new Deployment(), new Launcher() {
            final PrismLaunch prism = new PrismLaunch();
            public void prepare(PrismDiscovery.Instance target, Path executable, Consumer<String> log) throws Exception { prism.command(target, executable); prism.verifyExecutable(executable, log); }
            public void launch(PrismDiscovery.Instance target, Path executable, Consumer<String> log) throws Exception { prism.launch(target, executable, log); }
        }, new RunningCheck()::requireClosed);
    }
    PackService(Path project, Builder builder, Deployment deployment, Launcher launcher, ClosedCheck closed) {
        this.project = project; this.builder = builder; this.deployment = deployment; this.launcher = launcher; this.closed = closed;
    }
    Deployment.Result execute(PrismDiscovery.Instance target, boolean confirmedClosed, boolean launch, Path executable, Cancellation cancel, Consumer<String> log) throws Exception {
        Compatibility compatibility = new Compatibility(project);
        compatibility.instance(target).requireSupported();
        closed.check(target, confirmedClosed); cancel.check();
        if (launch) launcher.prepare(target, executable, log);
        // Frozen immutable target and cross-process instance lock cover build, replace and launch.
        try (var lease = deployment.acquire(target)) {
            deployment.requireRecovered(lease);
            log.accept("Frozen operation target: " + target.name() + " [" + target.id() + "]\nLoader: " + target.loader() + " " + target.loaderVersion() + "\nDestination: " + target.mods());
            var artifact = builder.build(project, target, cancel, log); cancel.check();
            var refreshed = new PrismDiscovery().inspect(target.directory(), target.root());
            if (!refreshed.equals(target)) throw new IOException("Instance metadata/layout changed during build. Refresh and review before retrying.");
            compatibility.instance(refreshed).requireSupported();
            ModMetadata.production(artifact.path(), target.loader());
            compatibility.requirements(target, ModMetadata.read(artifact.path(), target.loader()).required()).requireSupported();
            closed.check(target, confirmedClosed); cancel.check();
            var result = deployment.install(lease, target, artifact, cancel, log);
            if (launch) {
                try { cancel.launchIfActive(() -> launcher.launch(target, executable, log)); }
                catch (Exception e) { throw new IOException("Deployment succeeded at " + result.destination() + ", but Prism was not launched: " + e.getMessage(), e); }
            }
            return result;
        }
    }
    void recover(PrismDiscovery.Instance target, boolean confirmedClosed, Consumer<String> log) throws Exception {
        closed.check(target, confirmedClosed);
        try (var lease = deployment.acquire(target)) { deployment.recover(lease, target, log); }
    }
}
