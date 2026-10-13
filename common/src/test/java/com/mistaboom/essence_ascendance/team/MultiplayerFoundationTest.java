package com.mistaboom.essence_ascendance.team;

import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.machine.MachinePoolAccess;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Real NBT/player accounting checks and provider contract fixtures; not a multiplayer launch. */
public final class MultiplayerFoundationTest {
    private static int assertions;
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);
    private static final Set<PoolCapability> MEMBER = EnumSet.of(PoolCapability.USE,
            PoolCapability.CONFIGURE, PoolCapability.SPEND, PoolCapability.APPROVE_REQUEST);

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        EssenceTypes.init();
        AscendanceTiers.init();
        normalizedMembership();
        readinessAndAuthority();
        savedSoloAndAccounting();
        System.out.println("MultiplayerFoundationTest: " + assertions + " assertions passed");
    }

    private static void normalizedMembership() {
        PoolId identity = PoolId.newNative(PoolId.Kind.TEAM);
        List<UUID> sourceMembers = new ArrayList<>(List.of(B, A, B));
        Set<PoolCapability> sourceGrants = EnumSet.allOf(PoolCapability.class);
        Map<UUID, Set<PoolCapability>> grants = new HashMap<>(Map.of(A, sourceGrants, B, MEMBER));
        MembershipSnapshot team = new MembershipSnapshot(identity, sourceMembers, grants, 1, 1);
        sourceMembers.clear(); sourceGrants.clear(); grants.clear();
        check(team.members().equals(List.of(A, B)), "Roster is unique, sorted and independent of input mutation");
        check(team.allows(A, PoolCapability.MANAGE) && !team.allows(B, PoolCapability.MANAGE),
                "Management remains distinct from ordinary membership");
        check(team.allows(B, PoolCapability.SPEND) && team.allows(B, PoolCapability.APPROVE_REQUEST),
                "Member policy allows spending and approval");
        check(!team.allows(C, PoolCapability.SPEND), "Nonmember cannot inherit financial permissions");
        rejects(() -> team.members().add(C));
        rejects(() -> team.permissions().put(C, MEMBER));
        rejects(() -> team.permissions().get(A).clear());
        rejects(() -> new MembershipSnapshot(identity, List.of(), Map.of(), 1, 1));
        rejects(() -> new MembershipSnapshot(identity, List.of(A), Map.of(C, MEMBER), 1, 1));
        rejects(() -> new MembershipSnapshot(identity, List.of(A), Map.of(), 0, 1));
        rejects(() -> new MembershipSnapshot(PoolId.newNative(PoolId.Kind.SOLO), List.of(A, B), Map.of(), 1, 1));
        check(!identity.id().equals(A) && !identity.id().equals(B), "Team identity is allocated independently of players");
        check(!identity.equals(new PoolId(MembershipAuthority.FTB_TEAMS, PoolId.Kind.TEAM, identity.id())),
                "Different authorities cannot collide on a UUID");
        check(!identity.equals(new PoolId(MembershipAuthority.NATIVE, PoolId.Kind.SOLO, identity.id())),
                "Team and solo namespaces cannot collide");

        MembershipSnapshot.Change noop = team.reconcile(List.of(B, A, B), team.permissions());
        check(noop.after().equals(team) && !noop.membershipChanged() && !noop.permissionsChanged(),
                "Roster order/duplicates cannot invalidate jobs");
        MembershipSnapshot.Change roles = team.reconcile(team.members(), Map.of(A, MEMBER, B, MEMBER));
        check(!roles.membershipChanged() && roles.permissionsChanged()
                && roles.after().permissionRevision() == 2 && roles.after().membershipRevision() == 1,
                "Permission-only changes do not masquerade as a membership transition");
        MembershipSnapshot.Change joining = roles.after().reconcile(List.of(C, B, A), Map.of(A, MEMBER, B, MEMBER, C, MEMBER));
        check(joining.added().equals(Set.of(C)) && joining.removed().isEmpty()
                && joining.after().membershipRevision() == 2, "Complete roster includes offline joining members");
        MembershipSnapshot.Change leaving = joining.after().reconcile(List.of(C, B), Map.of(B, MEMBER, C, MEMBER));
        check(leaving.removed().equals(Set.of(A)) && leaving.added().isEmpty(), "Reconciliation exposes detached shares");
        check(leaving.after().pool().equals(identity) && team.members().equals(List.of(A, B)),
                "Transitions preserve identity and do not publish or mutate prior state");
        check(!MachinePoolAccess.allowsPersonal(team, A, A, PoolCapability.SPEND),
                "Unadopted personal binding cannot spend a teamed owner's underlying share");
        check(MembershipSnapshot.load(leaving.after().save()).equals(leaving.after()), "Revisions and grants survive NBT");
        rejects(() -> new MembershipSnapshot(identity, List.of(A), Map.of(), Long.MAX_VALUE, 1)
                .reconcile(List.of(A, B), Map.of()));
        CompoundTag corrupted = team.save();
        corrupted.getList("members", Tag.TAG_COMPOUND).getCompound(0).putInt("capabilities", 1 << 20);
        rejects(() -> MembershipSnapshot.load(corrupted));
        CompoundTag duplicates = team.save();
        duplicates.getList("members", Tag.TAG_COMPOUND).add(duplicates.getList("members", Tag.TAG_COMPOUND).get(0).copy());
        rejects(() -> MembershipSnapshot.load(duplicates));
    }

    private static void readinessAndAuthority() {
        ProviderReadiness ready = new ProviderReadiness(ProviderReadiness.Status.AVAILABLE, "fixture", "Complete roster");
        ProviderReadiness waiting = new ProviderReadiness(ProviderReadiness.Status.NOT_READY, "fixture", "Server API starting");
        Map<MembershipAuthority, ProviderReadiness> all = Map.of(MembershipAuthority.NATIVE, ready,
                MembershipAuthority.FTB_TEAMS, ready, MembershipAuthority.OPENPAC, ready);
        check(MembershipAuthority.select(MembershipAuthority.Selection.AUTO, null, all).authority()
                == MembershipAuthority.FTB_TEAMS, "Initial AUTO prefers supported FTB Teams");
        check(MembershipAuthority.select(MembershipAuthority.Selection.AUTO, null,
                Map.of(MembershipAuthority.NATIVE, ready, MembershipAuthority.OPENPAC, ready)).authority()
                == MembershipAuthority.OPENPAC, "Initial AUTO uses supported OpenPAC next");
        check(MembershipAuthority.select(MembershipAuthority.Selection.AUTO, null,
                Map.of(MembershipAuthority.NATIVE, ready)).usable(), "Provider-free native selection is usable");
        var unready = MembershipAuthority.select(MembershipAuthority.Selection.AUTO, null,
                Map.of(MembershipAuthority.NATIVE, ready, MembershipAuthority.FTB_TEAMS, waiting,
                        MembershipAuthority.OPENPAC, ready));
        check(unready.authority() == MembershipAuthority.FTB_TEAMS && !unready.usable(),
                "Supported but starting FTB does not briefly establish another authority");
        var retained = MembershipAuthority.select(MembershipAuthority.Selection.AUTO, MembershipAuthority.FTB_TEAMS,
                Map.of(MembershipAuthority.NATIVE, ready));
        check(retained.authority() == MembershipAuthority.FTB_TEAMS && !retained.usable(),
                "Established authority remains unavailable instead of silently becoming native");
        var changed = MembershipAuthority.select(MembershipAuthority.Selection.OPENPAC, MembershipAuthority.NATIVE, all);
        check(changed.authorityChangeRequired() && !changed.usable(), "Explicit authority change requires a safe boundary");
        check(MembershipAuthority.select(MembershipAuthority.Selection.OPENPAC, null, all).usable(),
                "Explicit initial authority selection works without reconciliation");
        check(!MembershipAuthority.select(MembershipAuthority.Selection.FTB_TEAMS, null, Map.of()).usable(),
                "Explicit unavailable provider does not fall back");
        ProviderReadiness incomplete = new ProviderReadiness(ProviderReadiness.Status.AVAILABLE, "fixture", "Incomplete roster", false);
        check(!MembershipAuthority.ready(incomplete), "Available API alone cannot establish complete membership");
        check(!MembershipAuthority.select(MembershipAuthority.Selection.AUTO, null,
                Map.of(MembershipAuthority.NATIVE, ready, MembershipAuthority.FTB_TEAMS, incomplete)).usable(),
                "An incomplete supported roster cannot cause a temporary native fallback");
        var unknown = new MembershipResolution(waiting, Optional.empty());
        rejects(unknown::requireReady);
        rejects(() -> new MembershipResolution(ready, Optional.empty()));
        MembershipSnapshot snapshot = new PoolMembershipState().resolveSolo(A).requireReady();
        rejects(() -> new MembershipResolution(waiting, Optional.of(snapshot)));
        rejects(() -> new MembershipResolution(incomplete, Optional.of(snapshot)));
    }

    private static void savedSoloAndAccounting() {
        EssenceSavedData saved = new EssenceSavedData();
        saved.setDirty(false);
        MembershipSnapshot solo = saved.resolvePool(A).requireReady();
        check(saved.isDirty() && solo.members().equals(List.of(A)), "First solo resolution persists metadata");
        saved.setDirty(false);
        check(saved.resolvePool(A).requireReady().equals(solo) && !saved.isDirty(), "Repeated resolution is stable and read-only");
        check(!saved.resolvePool(B).requireReady().pool().equals(solo.pool()), "Distinct players have distinct persistent pools");
        for (PoolCapability capability : PoolCapability.values())
            check(MachinePoolAccess.allowsPersonal(solo, A, A, capability), "Solo owner receives " + capability);
        check(!MachinePoolAccess.allowsPersonal(solo, A, B, PoolCapability.USE), "Other player cannot use personal machine");
        check(!MachinePoolAccess.allowsPersonal(solo, B, B, PoolCapability.SPEND), "Binding must match funding membership");
        rejects(() -> solo.reconcile(List.of(B), Map.of(B, MEMBER)));

        for (var essence : EssenceTypes.ORDERED) {
            saved.addEssence(A, essence, 11);
            saved.addCrucibleStored(A, essence, 31);
            saved.addCrucibleStored(B, essence, 7);
            saved.setDirty(false);
            check(!saved.removeCrucibleStoredExact(A, essence, 32) && !saved.isDirty(), "Insufficient exact spend is inert");
            check(saved.transferCrucibleToAvailable(A, essence, 9) == 9, "Solo transfer uses existing accounting");
            check(saved.getPlayerData(A).getAvailable(essence) == 20
                    && saved.getPlayerData(A).getCrucibleStored(essence) == 22
                    && saved.getPlayerData(B).getCrucibleStored(essence) == 7, "Transfer preserves personal balances and other pools");
            check(saved.removeCrucibleStoredExact(A, essence, 2), "Exact spend succeeds when funded");
        }
        var offense = EssenceTypes.OFFENSE;
        saved.setEssence(A, offense, Long.MAX_VALUE - 2);
        check(saved.transferCrucibleToAvailable(A, offense, 100) == 2
                && saved.getPlayerData(A).getCrucibleStored(offense) == 18, "Available overflow retains untransferred reservoir");
        saved.addCrucibleStored(C, offense, Long.MAX_VALUE);
        EssenceSavedData beforeRoundTrip = saved;
        rejects(() -> beforeRoundTrip.addCrucibleStored(C, offense, 1));
        check(saved.getPlayerData(C).getCrucibleStored(offense) == Long.MAX_VALUE, "Rejected overflow leaves existing accounting intact");
        CompoundTag serialized = saved.save(new CompoundTag(), null);
        check(!serialized.getCompound("pool_membership").contains("balance"), "Membership metadata has no independent treasury");
        saved = EssenceSavedData.load(serialized, null);
        check(saved.resolvePool(A).requireReady().equals(solo), "SavedData restart retains identity, roster and revisions");
        check(saved.getPlayerData(B).getCrucibleStored(offense) == 7, "Offline account survives restart independently");
        saved.clearAll(A);
        check(saved.resolvePool(A).requireReady().equals(solo), "Personal progression reset does not reassign membership");
        CompoundTag old = serialized.copy(); old.putInt("data_version", 6);
        rejects(() -> EssenceSavedData.load(old, null));
        CompoundTag missing = serialized.copy(); missing.remove("pool_membership");
        rejects(() -> EssenceSavedData.load(missing, null));
        CompoundTag duplicate = serialized.copy();
        var solos = duplicate.getCompound("pool_membership").getCompound("solos");
        solos.getCompound(B.toString()).put("pool", solos.getCompound(A.toString()).getCompound("pool").copy());
        rejects(() -> EssenceSavedData.load(duplicate, null));
        CompoundTag external = serialized.copy(); external.getCompound("pool_membership").putString("authority", "FTB_TEAMS");
        rejects(() -> EssenceSavedData.load(external, null));
    }

    private static void check(boolean condition, String detail) {
        assertions++;
        if (!condition) throw new AssertionError(detail);
    }

    private static void rejects(Runnable operation) {
        assertions++;
        try { operation.run(); }
        catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException | ArithmeticException expected) { return; }
        throw new AssertionError("Invalid operation was accepted");
    }
}
