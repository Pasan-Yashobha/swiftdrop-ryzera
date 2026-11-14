package lk.swiftdrop.notification.admin;

import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lk.swiftdrop.notification.admin.dto.NotificationView;
import lk.swiftdrop.notification.admin.dto.SmsInboxItem;
import lk.swiftdrop.notification.admin.dto.TemplateView;
import lk.swiftdrop.notification.admin.dto.UpdateTemplateRequest;
import lk.swiftdrop.notification.common.NotFoundException;
import lk.swiftdrop.notification.common.PageRequests;
import lk.swiftdrop.notification.common.PageResponse;
import lk.swiftdrop.notification.notification.Channel;
import lk.swiftdrop.notification.notification.Notification;
import lk.swiftdrop.notification.notification.NotificationDispatcher;
import lk.swiftdrop.notification.notification.NotificationRepository;
import lk.swiftdrop.notification.notification.NotificationStatus;
import lk.swiftdrop.notification.sms.MockSmsMessageRepository;
import lk.swiftdrop.notification.template.NotificationTemplate;
import lk.swiftdrop.notification.template.NotificationTemplateRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin API behind the Notification Logs page (integration design section 8). {@code ROLE_ADMIN} only (security
 * configuration). Routed by the gateway as {@code /api/v1/admin/notifications/**}.
 */
@RestController
@RequestMapping("/api/v1/admin/notifications")
public class NotificationAdminController {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final NotificationRepository notifications;
    private final NotificationDispatcher dispatcher;
    private final MockSmsMessageRepository smsInbox;
    private final NotificationTemplateRepository templates;
    private final Clock clock;

    public NotificationAdminController(NotificationRepository notifications, NotificationDispatcher dispatcher,
                                       MockSmsMessageRepository smsInbox, NotificationTemplateRepository templates,
                                       Clock clock) {
        this.notifications = notifications;
        this.dispatcher = dispatcher;
        this.smsInbox = smsInbox;
        this.templates = templates;
        this.clock = clock;
    }

    @GetMapping
    public PageResponse<NotificationView> list(
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) Channel channel,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Specification<Notification> filter = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (channel != null) {
                predicates.add(cb.equal(root.get("channel"), channel));
            }
            if (eventType != null && !eventType.isBlank()) {
                predicates.add(cb.equal(root.get("eventType"), eventType.trim()));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(notifications.findAll(filter, PageRequests.of(page, size, NEWEST_FIRST)),
                NotificationView::of);
    }

    /** Manual resend of a FAILED notification: one immediate attempt with fresh contact details. */
    @PostMapping("/{id}/resend")
    public NotificationView resend(@PathVariable UUID id) {
        return NotificationView.of(dispatcher.resend(id));
    }

    /** What the development SMS provider received, newest first. */
    @GetMapping("/sms-inbox")
    public PageResponse<SmsInboxItem> smsInbox(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(smsInbox.findAll(PageRequests.of(page, size,
                Sort.by(Sort.Direction.DESC, "receivedAt"))), SmsInboxItem::of);
    }

    @GetMapping("/templates")
    public List<TemplateView> templates() {
        return templates.findAllByOrderByEventTypeAscChannelAsc().stream().map(TemplateView::of).toList();
    }

    @PutMapping("/templates/{id}")
    @Transactional
    public TemplateView updateTemplate(@PathVariable UUID id, @Valid @RequestBody UpdateTemplateRequest request) {
        NotificationTemplate template = templates.findById(id)
                .orElseThrow(() -> new NotFoundException("Template not found"));
        String subject = template.getChannel() == Channel.SMS ? null : blankToNull(request.subject());
        template.update(subject, request.body(), request.active(), clock.instant());
        return TemplateView.of(templates.saveAndFlush(template));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
