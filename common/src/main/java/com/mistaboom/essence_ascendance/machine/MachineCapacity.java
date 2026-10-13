package com.mistaboom.essence_ascendance.machine;
import com.mistaboom.essence_ascendance.data.ReservoirService;
import com.mistaboom.essence_ascendance.team.MembershipSnapshot;
import java.math.BigInteger;
public final class MachineCapacity {
    private MachineCapacity() { }
    public static BigInteger limit(long structure, MembershipSnapshot membership) {
        return BigInteger.valueOf(structure).multiply(BigInteger.valueOf(membership == null ? 0 : membership.members().size()));
    }
    public static boolean accepts(BigInteger storedUnits, BigInteger additions, BigInteger limit) {
        return additions.signum() >= 0 && storedUnits.add(additions).compareTo(limit.multiply(ReservoirService.SCALE)) <= 0;
    }
    public static int visualFill(BigInteger storedUnits, BigInteger limit) {
        if (limit.signum() <= 0) return 0;
        return storedUnits.multiply(BigInteger.valueOf(10000)).divide(limit.multiply(ReservoirService.SCALE))
                .min(BigInteger.valueOf(10000)).intValueExact();
    }
}
