import java.time.LocalDate;

public final class TenantStorageServiceTest {
    public static void main(String[] args) {
        TenantStorageService service = new TenantStorageService(null);
        LocalDate today = LocalDate.of(2026, 10, 23);
        var urgent = new TenantStorageService.MaintenanceRequest("17", "window", today.plusDays(1), true);
        var routine = new TenantStorageService.MaintenanceRequest("18", "paint", today.plusDays(1), false);
        if (!service.reminderFor(urgent, today).reason().equals("priority-inspection")) throw new AssertionError("urgent request must be prioritized");
        if (!service.reminderFor(routine, today).reason().equals("routine-inspection")) throw new AssertionError("routine request must remain routine");
        if (InfraiClient.objectCount("{\"objects\":[]}") != 0) throw new AssertionError("empty bucket");
        if (InfraiClient.objectCount("{\"objects\":[{\"key\":\"a,b\",\"nested\":[1,2]},{\"key\":\"b\"}]}") != 2)
            throw new AssertionError("count top-level objects");
        System.out.println("TenantStorageServiceTest: passed");
    }
}
