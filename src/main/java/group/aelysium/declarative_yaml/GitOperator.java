package group.aelysium.declarative_yaml;

import com.github.git24j.core.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.*;

public class GitOperator {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Config config;
    private final Set<LiveConfig> liveConfigs = Collections.newSetFromMap(new ConcurrentHashMap<>());

    protected GitOperator(@NotNull Config config) {
        this.config = config;
        this.executor.schedule(this::handleLiveConfigs, this.config.fetchPeriodAmount(), this.config.fetchPeriodUnit());
    }

    public void sync() {
        File file = this.config.location().toFile();
        if(!file.exists()) file.mkdirs();

        try (Repository repository = Clone.cloneRepo(this.config.repository().toASCIIString(), this.config.location(), null)) {
            Remote origin = Remote.lookup(repository, "origin");
            Reference branch = Branch.lookup(repository, this.config.branch(), Branch.BranchType.ALL);
            if (branch == null) throw new IllegalStateException("Branch not found: " + this.config.branch());

            Checkout.Options options = Checkout.Options.defaultOptions();
            options.setStrategy(EnumSet.of(
                    Checkout.StrategyT.SAFE,
                    Checkout.StrategyT.USE_THEIRS,
                    Checkout.StrategyT.FORCE
            ));
            Checkout.tree(repository, branch.peel(GitObject.Type.TREE), options);
            repository.setHead(branch.name());

            origin.fetch(null, null, null);
        }
    }

    public File fetch(Path path) {
        if(!path.startsWith(this.config.location))
            return Path.of(this.config.location.toString(), path.toString()).toFile();
        return path.toFile();
    }

    public void liveConfig(@NotNull LiveConfig config) {
        this.liveConfigs.add(config);
    }

    protected void handleLiveConfigs() {
        this.sync();
        this.liveConfigs.forEach(c -> {
            try {
                DeclarativeYAML.reload(c.instance(), c.printer());
            } catch (Exception e) {
                if(c.onFail() == null) return;
                c.onFail().accept(e);
            }
        });
        this.executor.schedule(this::handleLiveConfigs, this.config.fetchPeriodAmount(), this.config.fetchPeriodUnit());
    }

    public static class Config {
        private final URI repository;
        private String branch = "main";
        private Path location = Path.of("");
        private long fetchPeriodAmount = 1;
        private TimeUnit fetchPeriodUnit = TimeUnit.MINUTES;

        public Config(@NotNull URI repository) {
            if(!(repository.getScheme().equalsIgnoreCase("http") || repository.getScheme().equalsIgnoreCase("https")))
                throw new IllegalArgumentException("The repository URI must point to a remote resource on the www.");
            this.repository = repository;
        }
        public URI repository() { return this.repository; }

        public Path location() { return this.location; }
        public Config location(@NotNull Path location) throws IllegalArgumentException {
            if(!location.toFile().isDirectory()) throw new IllegalArgumentException("The local location for the git repository must be a directory.");
            this.location = location;
            return this;
        }

        public long fetchPeriodAmount() { return this.fetchPeriodAmount; }
        public TimeUnit fetchPeriodUnit() { return this.fetchPeriodUnit; }
        public Config fetchPeriod(long amount, @NotNull TimeUnit unit) {
            this.fetchPeriodAmount = amount;
            this.fetchPeriodUnit = unit;
            return this;
        }

        public String branch() { return this.branch; }
        public Config branch(@NotNull String branch) { this.branch = branch; return this; }

        public GitOperator build() {
            return new GitOperator(this);
        }
    }
}