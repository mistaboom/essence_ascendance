package com.mistaboom.essence_ascendance.team;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.balance.engine.OptionalIntegration;
import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.platform.Platform;
import net.minecraft.server.MinecraftServer;
import java.util.*;

/** Shared server lifecycle. Discovery/selection occur once, never inside gameplay consumers. */
public final class MembershipRuntime {
    private static final Map<MinecraftServer, Session> SESSIONS = new IdentityHashMap<>();
    private static Map<MembershipAuthority, MembershipProviders.Installed> providers = Map.of();
    private MembershipRuntime() { }
    private static final class Session {
        final EssenceSavedData saved;
        final MembershipSettlement settlement;
        final MembershipCaptureSchedule schedule = new MembershipCaptureSchedule();
        final MembershipProvider provider;
        final boolean blocked;
        final Map<MembershipAuthority, ProviderReadiness> observedProviders = new EnumMap<>(MembershipAuthority.class);
        boolean started;
        Preparation preparation;
        String notice;
        ProviderReadiness pendingFailure;
        Session(EssenceSavedData saved, MembershipProvider provider, boolean blocked) {
            this.saved = saved; this.provider = provider; this.blocked = blocked;
            this.settlement = new MembershipSettlement(saved);
        }
    }
    static final class Preparation {
        static final int REVIEW_TICKS = 6000;
        final MembershipAuthority target;
        Map<PoolId, MembershipRoster> candidate;
        MembershipActivation receipt;
        private int ticks = REVIEW_TICKS;
        Preparation(MembershipAuthority target) { this.target = target; }
        int ticksRemaining() { return ticks; }
        boolean advanceTick() { if (ticks > 0) ticks--; return ticks == 0; }
        String expiredMessage() {
            return "Preparation for " + target.id() + " expired after 6,000 server ticks (about 5 minutes). "
                    + "The established authority was retained. Run /essence admin membership prepare " + target.id() + " again.";
        }
    }
    public record Diagnostics(MembershipAuthority established, ProviderReadiness readiness,
                              Map<MembershipAuthority, ProviderReadiness> providers, Set<PoolId> confirmedDeletions,
                              MembershipAuthority target, UUID activationToken, int activationTicksRemaining,
                              int affectedPlayers, int affectedTeams, String notice) { }
    public static void init() {
        Map<String, String> mods = new HashMap<>();
        MembershipProviders.entries().stream().map(MembershipProviders.Entry::modId).distinct().forEach(id -> {
            if (Platform.isModLoaded(id)) mods.put(id, Platform.getMod(id).getVersion());
        });
        providers = MembershipProviders.discover(mods, Platform.isFabric() ? "fabric" : Platform.isModLoaded("neoforge") ? "neoforge" : "unsupported",
                MembershipRuntime::invalidate);
        LifecycleEvent.SERVER_LEVEL_LOAD.register(level -> {
            if (level == level.getServer().overworld()) start(level.getServer());
        });
        LifecycleEvent.SERVER_STARTED.register(server -> {
            Session session = SESSIONS.get(server);
            if (session != null) { session.started = true; session.schedule.invalidate(); }
        });
        TickEvent.SERVER_PRE.register(MembershipRuntime::tick);
        LifecycleEvent.SERVER_STOPPING.register(server -> {
            Session session = SESSIONS.get(server);
            if (session != null) { session.started = false; session.saved.reservoirs().pause(status("Server shutting down")); }
        });
        LifecycleEvent.SERVER_STOPPED.register(server -> {
            Session session = SESSIONS.remove(server);
            if (session != null) session.saved.membershipProbe(ignored -> { });
        });
    }
    public static MembershipProviders.Installed provider(MembershipAuthority authority) { return providers.get(authority); }
    /** Presentation reads the same lifecycle that denies authorization; it never guesses provider readiness. */
    public static String interfaceState(MinecraftServer server, MembershipResolution resolution) {
        var session = SESSIONS.get(server);
        if (session != null && (session.blocked || session.preparation != null)) return "AUTHORITY_CHANGE_REQUIRED";
        if (session != null && !session.started || resolution.readiness().status() == ProviderReadiness.Status.NOT_READY) return "INITIALIZING";
        return resolution.state().name();
    }
    private static ProviderReadiness status(String detail) {
        return new ProviderReadiness(ProviderReadiness.Status.NOT_READY, "stage-4", detail);
    }
    private static MembershipAuthority.Decision decision(EssenceSavedData saved) {
        // An actual API failure makes native membership the durable authority. A stale
        // startup property must not undo recovery on every restart; explicit activation can.
        if (saved.membershipAuthority() == MembershipAuthority.NATIVE && saved.hasMembershipFallback())
            return MembershipAuthority.select(MembershipAuthority.Selection.AUTO, MembershipAuthority.NATIVE,
                    MembershipProviders.statuses(providers));
        return MembershipAuthority.select(MembershipAuthority.selection(System.getProperty("essence.membershipAuthority", "auto")),
                saved.establishedMembershipAuthority(), MembershipProviders.statuses(providers));
    }
    private static void start(MinecraftServer server) {
        if (SESSIONS.containsKey(server)) return;
        EssenceSavedData saved = EssenceSavedData.get(server);
        try {
            var decision = decision(saved);
            if (!decision.authorityChangeRequired()) saved.establishMembershipAuthority(decision.authority());
            connect(server, saved, decision.authorityChangeRequired());
            if (decision.authorityChangeRequired()) saved.reservoirs().pause(status("Requested " + decision.authority().id()
                    + " differs from persisted " + saved.membershipAuthority().id() + "; use /essence admin membership prepare <provider> then activate <token>"));
            EssenceAscendance.LOGGER.info("Membership authority {}: {}", saved.membershipAuthority().id(), saved.reservoirs().readiness());
        } catch (IllegalArgumentException failure) {
            connect(server, saved, true); saved.reservoirs().pause(status(failure.getMessage()));
            OptionalIntegration.warn("membership selection", failure.getMessage());
        }
    }
    private static void connect(MinecraftServer server, EssenceSavedData saved, boolean blocked) {
        var installed = providers.get(saved.membershipAuthority());
        Session session = new Session(saved, installed == null ? null : installed.provider(), blocked);
        SESSIONS.put(server, session);
        saved.membershipProbe(member -> {
            if (session.preparation != null || session.blocked || session.provider == null
                    || !MembershipAuthority.ready(saved.reservoirs().readiness())) return;
            var attempt = OptionalIntegration.attempt("membership", "live authorization probe", () ->
                    session.provider.isCurrent(server, member, saved.reservoirs().settledTeam(member)));
            if (!attempt.succeeded() || !attempt.value().orElseThrow()) {
                String detail = "Live provider membership/permission changed or unavailable";
                session.observedProviders.put(saved.membershipAuthority(), status(detail));
                invalidateSession(session, detail);
                // This probe can run inside a financial operation. Finish denying that
                // operation, then perform the authority transition at the next server tick.
                if (!attempt.succeeded()) session.pendingFailure = failed(session.saved.membershipAuthority(),
                        "Live membership API failed: " + attempt.failure());
            }
        });
        if (blocked) session.settlement.invalidate("Authority activation required");
        else if (saved.membershipAuthority() != MembershipAuthority.NATIVE) {
            session.settlement.invalidate("External authority awaiting settled complete roster");
            if (session.provider == null) {
                ProviderReadiness readiness = installed == null
                        ? new ProviderReadiness(ProviderReadiness.Status.ABSENT, "absent", "Membership API is no longer installed")
                        : installed.readiness();
                saved.reservoirs().pause(readiness);
                fallback(server, session, readiness);
            }
        } else MembershipPublication.publish(saved, NativeMembershipService.READY,
                saved.reservoirs().settledTeams().stream().map(MembershipRoster::from).toList());
    }
    static boolean fallbackRequired(ProviderReadiness readiness) {
        return readiness.status() == ProviderReadiness.Status.FAILED
                || readiness.status() == ProviderReadiness.Status.ABSENT
                || readiness.status() == ProviderReadiness.Status.UNSUPPORTED;
    }
    private static ProviderReadiness failed(MembershipAuthority authority, String detail) {
        var installed = providers.get(authority);
        return new ProviderReadiness(ProviderReadiness.Status.FAILED,
                installed == null ? "unknown" : installed.readiness().version(), detail);
    }
    /** An unavailable startup epoch is not an incompatibility. Only an actual failure
     * switches authority, using saved ownership/rosters rather than guessing new members. */
    private static boolean fallback(MinecraftServer server, Session session, ProviderReadiness failure) {
        if (session.blocked || session.saved.membershipAuthority() == MembershipAuthority.NATIVE
                || !fallbackRequired(failure)) return false;
        MembershipAuthority previous = session.saved.membershipAuthority();
        String detail = previous.displayName() + " integration failed (" + failure.detail()
                + "); using Essence Ascendance teams with the last confirmed ownership and balances";
        var attempt = OptionalIntegration.attempt("membership", "preserve ownership and switch to native teams", () ->
                MembershipFallback.activate(session.saved, detail));
        if (!attempt.succeeded()) {
            session.saved.reservoirs().pause(failed(previous, "Native fallback could not preserve saved ownership: " + attempt.failure()));
            return false;
        }
        connect(server, session.saved, false);
        Session replacement = SESSIONS.get(server);
        replacement.started = session.started;
        replacement.observedProviders.putAll(session.observedProviders);
        replacement.observedProviders.put(previous, failure);
        replacement.notice = detail;
        EssenceAscendance.LOGGER.warn("{}", detail);
        return true;
    }
    private static void invalidateSession(Session session, String detail) {
        session.settlement.invalidate(detail); session.schedule.invalidate();
    }
    private static void invalidate(MinecraftServer server, MembershipAuthority authority) {
        if (!server.isSameThread()) { server.execute(() -> invalidate(server, authority)); return; }
        Session session = SESSIONS.get(server);
        if (session == null) return;
        session.observedProviders.put(authority, status("Provider event; awaiting a current complete roster"));
        if (session.preparation != null && session.preparation.target == authority) {
            session.preparation.candidate = null; session.preparation.receipt = null; session.schedule.invalidate();
        }
        if (!session.blocked && session.saved.membershipAuthority() == authority)
            invalidateSession(session, "Provider event; complete roster awaits settlement");
    }
    private static MembershipProvider.Observation capture(MinecraftServer server, MembershipAuthority authority) {
        var installed = providers.get(authority);
        var observation = authority == MembershipAuthority.NATIVE
                ? new MembershipProvider.Observation(NativeMembershipService.READY, List.of())
                : installed.provider() == null ? new MembershipProvider.Observation(installed.readiness(), List.of()) : installed.provider().capture(server);
        SESSIONS.get(server).observedProviders.put(authority, observation.readiness());
        return observation;
    }
    private static void tick(MinecraftServer server) {
        Session session = SESSIONS.get(server);
        if (session == null || !session.started) return;
        if (session.pendingFailure != null) {
            ProviderReadiness failure = session.pendingFailure;
            session.pendingFailure = null;
            if (fallback(server, session, failure)) return;
        }
        if (session.preparation != null && session.preparation.advanceTick()) {
            String notice = session.preparation.expiredMessage();
            cancel(server); SESSIONS.get(server).notice = notice;
            EssenceAscendance.LOGGER.info("{}", notice);
            return;
        }
        if ((session.preparation == null && (session.blocked || session.provider == null)) || !session.schedule.due()) return;
        var attempt = OptionalIntegration.attempt("membership", "capture/settle complete roster", () -> {
            if (session.preparation != null) {
                Preparation preparation = session.preparation;
                var observation = capture(server, preparation.target);
                if (!MembershipAuthority.ready(observation.readiness())) {
                    preparation.candidate = null; preparation.receipt = null;
                    session.saved.reservoirs().pause(observation.readiness()); session.schedule.captured(false, true);
                } else {
                    var next = MembershipPublication.normalize(preparation.target, observation.teams());
                    if (next.equals(preparation.candidate)) {
                        if (preparation.receipt == null) preparation.receipt = MembershipActivation.prepare(session.saved, preparation.target, observation);
                    } else { preparation.candidate = next; preparation.receipt = null; }
                    session.schedule.captured(true, preparation.receipt == null);
                }
            } else {
                var observation = capture(server, session.saved.membershipAuthority());
                if (fallback(server, session, observation.readiness())) return Boolean.TRUE;
                session.settlement.observe(observation);
                session.schedule.captured(MembershipAuthority.ready(observation.readiness()), session.settlement.pending());
            }
            return Boolean.TRUE;
        });
        if (!attempt.succeeded()) {
            MembershipAuthority target = session.preparation == null ? session.saved.membershipAuthority() : session.preparation.target;
            ProviderReadiness failure = failed(target, "Provider capture/reconciliation failed: " + attempt.failure());
            session.observedProviders.put(target, failure);
            if (session.preparation == null && fallback(server, session, failure)) return;
            if (session.preparation != null) { session.preparation.candidate = null; session.preparation.receipt = null; }
            session.settlement.invalidate("Provider capture/reconciliation failed: " + attempt.failure()); session.schedule.captured(false, true);
        }
    }
    public static Diagnostics diagnosticSnapshot(MinecraftServer server) {
        Session session = Objects.requireNonNull(SESSIONS.get(server), "Membership server not initialized");
        Map<MembershipAuthority, ProviderReadiness> states = new EnumMap<>(MembershipAuthority.class);
        providers.forEach((authority, installed) -> {
            ProviderReadiness readiness = installed.readiness();
            if (session.started && installed.provider() != null)
                readiness = new ProviderReadiness(ProviderReadiness.Status.NOT_READY, readiness.version(),
                        "Adapter enabled; no live roster observed in this session");
            states.put(authority, readiness);
        });
        states.putAll(session.observedProviders);
        Preparation preparation = session.preparation;
        MembershipActivation receipt = preparation == null ? null : preparation.receipt;
        return new Diagnostics(session.saved.membershipAuthority(), session.saved.reservoirs().readiness(),
                Map.copyOf(states), session.settlement.confirmedDeletions(), preparation == null ? null : preparation.target,
                receipt == null ? null : receipt.token(), preparation == null ? 0 : preparation.ticksRemaining(),
                receipt == null ? 0 : receipt.memberCount(), receipt == null ? 0 : receipt.teamCount(),
                session.notice == null && session.saved.hasMembershipFallback() ? session.saved.membershipFallbackReason() : session.notice);
    }
    public static String diagnostics(MinecraftServer server) {
        Session session = Objects.requireNonNull(SESSIONS.get(server), "Membership server not initialized");
        String result = "Established " + session.saved.membershipAuthority().id() + "; " + session.saved.reservoirs().readiness()
                + "; providers " + diagnosticSnapshot(server).providers() + "; confirmed deletions " + session.settlement.confirmedDeletions();
        if (session.preparation != null) result += session.preparation.receipt == null ? "; activation target awaiting two complete observations"
                : "; " + session.preparation.receipt.description();
        String notice = session.notice == null && session.saved.hasMembershipFallback()
                ? session.saved.membershipFallbackReason() : session.notice;
        if (notice != null) result += "; " + notice;
        return result;
    }
    public static String prepare(MinecraftServer server, String id) {
        Session session = Objects.requireNonNull(SESSIONS.get(server), "Membership server not initialized");
        var selection = MembershipAuthority.selection(id);
        if (selection == MembershipAuthority.Selection.AUTO) throw new IllegalArgumentException("Activation requires a stable explicit provider ID");
        var target = MembershipAuthority.valueOf(selection.name());
        if (target == session.saved.membershipAuthority()) throw new IllegalArgumentException("Authority already established; use reconcile");
        var installed = providers.get(target);
        if (target != MembershipAuthority.NATIVE && installed.provider() == null) throw new IllegalStateException(installed.readiness().detail());
        session.preparation = new Preparation(target); session.notice = null;
        invalidateSession(session, "Administrator preparing authority activation; access paused");
        return "Preparing " + target.id() + ". Review /essence admin membership status, then activate its token within 6,000 server ticks (about 5 minutes). Cancel resumes the persisted authority when configuration allows.";
    }
    public static String activate(MinecraftServer server, UUID token) {
        Session session = Objects.requireNonNull(SESSIONS.get(server), "Membership server not initialized");
        Preparation preparation = session.preparation;
        if (preparation == null) throw new IllegalStateException(session.notice == null
                ? "No activation prepared; use /essence admin membership prepare <provider>, then inspect status" : session.notice);
        if (preparation.receipt == null) throw new IllegalStateException("Target roster is not settled yet; inspect /essence admin membership status before activating");
        var attempt = OptionalIntegration.attempt("membership", "activate reviewed authority", () -> {
            preparation.receipt.activate(session.saved, token, capture(server, preparation.target)); return Boolean.TRUE;
        });
        if (!attempt.succeeded()) {
            session.observedProviders.put(preparation.target, status("Activation evidence needs review: " + attempt.failure()));
            preparation.receipt = null; preparation.candidate = null; session.schedule.invalidate();
            throw new IllegalStateException(attempt.failure());
        }
        connect(server, session.saved, false); SESSIONS.get(server).started = session.started;
        return "Activated " + session.saved.membershipAuthority().id() + "; use auto or this provider ID in essence.membershipAuthority for subsequent starts";
    }
    public static String cancel(MinecraftServer server) {
        Session old = Objects.requireNonNull(SESSIONS.get(server), "Membership server not initialized");
        boolean blocked;
        try { blocked = decision(old.saved).authorityChangeRequired(); } catch (IllegalArgumentException failure) { blocked = true; }
        connect(server, old.saved, blocked); SESSIONS.get(server).started = old.started;
        return blocked ? "Persisted authority retained; configuration still requires explicit activation" : "Persisted authority retained; reconciliation resumes";
    }
    public static String reconcile(MinecraftServer server) {
        Session session = Objects.requireNonNull(SESSIONS.get(server), "Membership server not initialized");
        if (session.preparation != null || session.blocked) throw new IllegalStateException("Resolve pending authority activation first");
        if (session.provider != null) invalidateSession(session, "Administrator requested provider reconciliation");
        return "Reconciliation requested for " + session.saved.membershipAuthority().id();
    }
}
