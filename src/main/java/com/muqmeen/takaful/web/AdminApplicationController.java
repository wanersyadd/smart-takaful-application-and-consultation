package com.muqmeen.takaful.web;

import com.muqmeen.takaful.domain.ApplicationStatus;
import com.muqmeen.takaful.domain.ConsultationApplication;
import com.muqmeen.takaful.domain.Quotation;
import com.muqmeen.takaful.service.AdminDashboardService;
import com.muqmeen.takaful.service.ApplicationService;
import com.muqmeen.takaful.service.ContactInquiryService;
import com.muqmeen.takaful.service.PlanFinderService;
import com.muqmeen.takaful.service.QuotationService;
import com.muqmeen.takaful.service.QuotationService.QuotationItemInput;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminApplicationController {

    private final ApplicationService applicationService;
    private final QuotationService quotationService;
    private final ContactInquiryService contactInquiryService;
    private final PlanFinderService planFinderService;
    private final AdminDashboardService adminDashboardService;

    public AdminApplicationController(ApplicationService applicationService,
                                      QuotationService quotationService,
                                      ContactInquiryService contactInquiryService,
                                      PlanFinderService planFinderService,
                                      AdminDashboardService adminDashboardService) {
        this.applicationService = applicationService;
        this.quotationService = quotationService;
        this.contactInquiryService = contactInquiryService;
        this.planFinderService = planFinderService;
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        List<ConsultationApplication> applications = applicationService.listForAdmin();
        model.addAttribute("applications", applications);
        model.addAttribute("statuses", ApplicationStatus.values());
        model.addAttribute("totalApplications", applications.size());
        model.addAttribute("submittedApplications", countStatuses(applications, ApplicationStatus.SUBMITTED, ApplicationStatus.UNDER_REVIEW));
        model.addAttribute("needsInfoApplications", countStatuses(applications, ApplicationStatus.NEEDS_INFO));
        model.addAttribute("quotedApplications", countStatuses(applications, ApplicationStatus.QUOTED, ApplicationStatus.PAYMENT_PENDING));
        model.addAttribute("paidApplications", countStatuses(applications, ApplicationStatus.PAID, ApplicationStatus.CLOSED));
        model.addAttribute("recentInquiries", contactInquiryService.recent());
        model.addAttribute("newInquiries", contactInquiryService.countNew());
        model.addAttribute("stats", adminDashboardService.stats(applications));
        model.addAttribute("greeting", greeting(LocalTime.now()));
        model.addAttribute("adminName", principal != null ? principal.getName() : "Admin");
        return "admin/dashboard";
    }

    private static String greeting(LocalTime time) {
        if (time.getHour() < 12) {
            return "Good morning";
        }
        return time.getHour() < 18 ? "Good afternoon" : "Good evening";
    }

    @PostMapping("/applications/{id}/delete")
    public String deleteApplication(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        applicationService.deleteApplication(id);
        redirectAttributes.addFlashAttribute("flashMessage", "Application deleted.");
        return "redirect:/admin/dashboard";
    }


    private long countStatuses(List<ConsultationApplication> applications, ApplicationStatus... statuses) {
        return applications.stream()
                .filter(application -> {
                    for (ApplicationStatus status : statuses) {
                        if (application.getStatus() == status) return true;
                    }
                    return false;
                })
                .count();
    }

    @GetMapping("/applications/{id}")
    public String detail(@PathVariable Long id, Model model) {
        ConsultationApplication application = applicationService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        model.addAttribute("application", application);
        model.addAttribute("app", application);
        model.addAttribute("productName", productName(application));
        model.addAttribute("statuses", ApplicationStatus.values());
        model.addAttribute("quotation", quotationService.getOrCreate(application));
        model.addAttribute("planFinder", planFinderService.forApplication(application).orElse(null));
        return "admin/application-detail";
    }

    @PostMapping("/applications/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @RequestParam ApplicationStatus status,
                               @RequestParam(required = false) String adminReviewNotes,
                               @RequestParam(required = false) String correctionRequest,
                               RedirectAttributes redirectAttributes) {
        applicationService.updateStatus(id, status, adminReviewNotes, correctionRequest);
        redirectAttributes.addFlashAttribute("flashMessage", "Application status updated.");
        return "redirect:/admin/applications/" + id;
    }

    @GetMapping("/applications/{id}/quotation")
    public String quotationForm(@PathVariable Long id, Model model) {
        ConsultationApplication application = applicationService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        Quotation quotation = quotationService.getOrCreate(application);
        model.addAttribute("application", application);
        model.addAttribute("app", application);
        model.addAttribute("productName", productName(application));
        model.addAttribute("quotation", quotation);
        model.addAttribute("form", QuotationForm.from(quotation));
        model.addAttribute("planFinder", planFinderService.forApplication(application).orElse(null));
        return "admin/quotation-form";
    }

    private String productName(ConsultationApplication application) {
        return application.getProduct() == null ? "Product unavailable" : application.getProduct().getName();
    }

    @PostMapping("/applications/{id}/quotation")
    public String saveQuotation(@PathVariable Long id,
                                @ModelAttribute QuotationForm form,
                                RedirectAttributes redirectAttributes) {
        ConsultationApplication application = applicationService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        quotationService.save(application, form.adminNotes, form.toItems());
        redirectAttributes.addFlashAttribute("flashMessage", "Quotation saved.");
        return "redirect:/admin/applications/" + id + "/quotation";
    }

    @PostMapping("/quotations/{id}/publish")
    public String publishQuotation(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Quotation quotation = quotationService.publish(id);
        redirectAttributes.addFlashAttribute("flashMessage", "Quotation published to customer.");
        return "redirect:/admin/applications/" + quotation.getApplication().getId();
    }

    public static class QuotationForm {
        public String adminNotes;
        public String itemsText;
        public List<QuotationItemRow> items = new ArrayList<>();

        static QuotationForm from(Quotation quotation) {
            QuotationForm form = new QuotationForm();
            form.adminNotes = quotation.getAdminNotes();
            quotation.getItems().stream()
                    .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                    .forEach(item -> form.items.add(QuotationItemRow.from(item)));
            if (form.items.isEmpty()) {
                form.items.add(new QuotationItemRow());
            }
            return form;
        }

        List<QuotationItemInput> toItems() {
            List<QuotationItemInput> rows = new ArrayList<>();
            if (items != null) {
                for (QuotationItemRow item : items) {
                    if (item == null || item.itemName == null || item.itemName.isBlank()) continue;
                    rows.add(new QuotationItemInput(
                            item.itemName.trim(),
                            item.description == null ? "" : item.description.trim(),
                            item.amount == null ? BigDecimal.ZERO : item.amount,
                            item.selected
                    ));
                }
            }
            if (!rows.isEmpty()) return rows;
            if (itemsText == null || itemsText.isBlank()) return rows;
            for (String line : itemsText.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.isBlank()) continue;
                String[] parts = trimmed.split("\\|", 4);
                rows.add(new QuotationItemInput(
                        parts[0].trim(),
                        parts.length > 1 ? parts[1].trim() : "",
                        parts.length > 2 ? parseAmount(parts[2].trim()) : BigDecimal.ZERO,
                        parts.length <= 3 || !"no".equalsIgnoreCase(parts[3].trim())
                ));
            }
            return rows;
        }

        private BigDecimal parseAmount(String raw) {
            try {
                return new BigDecimal(raw);
            } catch (NumberFormatException ex) {
                return BigDecimal.ZERO;
            }
        }

        public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }
        public void setItemsText(String itemsText) { this.itemsText = itemsText; }
        public List<QuotationItemRow> getItems() { return items; }
        public void setItems(List<QuotationItemRow> items) { this.items = items; }
    }

    public static class QuotationItemRow {
        public String itemName;
        public String description;
        public BigDecimal amount = BigDecimal.ZERO;
        public boolean selected = true;

        static QuotationItemRow from(com.muqmeen.takaful.domain.QuotationItem item) {
            QuotationItemRow row = new QuotationItemRow();
            row.itemName = item.getItemName();
            row.description = item.getDescription();
            row.amount = item.getAmount();
            row.selected = item.isSelected();
            return row;
        }

        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public boolean isSelected() { return selected; }
        public void setSelected(boolean selected) { this.selected = selected; }
    }
}
