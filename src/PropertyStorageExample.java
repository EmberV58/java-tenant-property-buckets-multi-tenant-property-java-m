import java.time.LocalDate;

public final class PropertyStorageExample {
    public static void main(String[] args) throws Exception {
        InfraiClient infrai = new InfraiClient();
        TenantStorageService service = new TenantStorageService(infrai);
        TenantStorageService.MaintenanceRequest request = new TenantStorageService.MaintenanceRequest(
                System.getenv().getOrDefault("INFRAI_TENANT_ID", "17"), "replace the classroom window latch", LocalDate.now().plusDays(1), true);
        TenantStorageService.OnboardingResult result = service.onboard(request);
        TenantStorageService.InspectionReminder reminder = service.reminderFor(request, LocalDate.now());
        System.out.println("tenant bucket=" + result.bucket());
        System.out.println("upload URL issued=" + !result.uploadUrl().isBlank());
        System.out.println("listed objects=" + result.listedObjects() + ", document found=" + result.found());
        System.out.println("reminder=" + reminder.reason() + " on " + reminder.date());
        String temporaryKeyId = infrai.idFromKeyResponse(infrai.createTemporaryKey());
        infrai.deleteObject(result.bucket(), result.objectKey());
        infrai.deleteBucket(result.bucket());
        infrai.revokeTemporaryKey(temporaryKeyId);
        System.out.println("tenant bucket deleted and temporary account key revoked for the offboarding rehearsal");
    }
}
