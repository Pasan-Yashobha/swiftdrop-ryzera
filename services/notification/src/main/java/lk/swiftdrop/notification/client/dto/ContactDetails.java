package lk.swiftdrop.notification.client.dto;

/** Contact details of an Identity user ({@code GET /internal/users/{id}}). Never logged as a whole. */
public record ContactDetails(String id, String email, String fullName, String phone) {

    @Override
    public String toString() {
        return "ContactDetails[id=" + id + "]";
    }
}
