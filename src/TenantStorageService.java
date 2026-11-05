import java.time.LocalDate;
import java.util.Objects;

final class TenantStorageService {
    record MaintenanceRequest(String tenantId, String description, LocalDate dueDate, boolean urgent) {}
    record InspectionReminder(String tenantId, LocalDate date, String reason) {}
    record OnboardingResult(String bucket, String uploadUrl, String objectKey, int listedObjects, boolean found) {}

    private final InfraiClient infrai;

    TenantStorageService(InfraiClient infrai) { this.infrai = infrai; }

    InspectionReminder reminderFor(MaintenanceRequest request, LocalDate today) {
        boolean near = !request.dueDate().isAfter(today.plusDays(2));
        String reason = request.urgent() && near ? "priority-inspection" : "routine-inspection";
        return new InspectionReminder(request.tenantId(), request.dueDate(), reason);
    }

    OnboardingResult onboard(MaintenanceRequest request) throws Exception {
        String bucket = "tenant-" + request.tenantId();
        String objectKey = "leases/" + request.tenantId() + "/inspection.pdf";
        infrai.createBucket(bucket);
        String uploadUrl = infrai.presignPut(bucket, objectKey);
        infrai.uploadDocument(uploadUrl, "%PDF-1.4\n% Tenant inspection document\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String listed = infrai.listObjects(bucket);
        String head = infrai.headObject(bucket, objectKey);
        int count = InfraiClient.objectCount(listed);
        boolean found = Boolean.TRUE.equals(Json.data(head).get("found"));
        return new OnboardingResult(bucket, uploadUrl, objectKey, count, found);
    }
}
