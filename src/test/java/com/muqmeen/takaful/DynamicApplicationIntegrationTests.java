package com.muqmeen.takaful;

import com.muqmeen.takaful.domain.ApplicationStatus;
import com.muqmeen.takaful.domain.ConsultationApplication;
import com.muqmeen.takaful.domain.Customer;
import com.muqmeen.takaful.domain.CustomerProfile;
import com.muqmeen.takaful.domain.FilePurpose;
import com.muqmeen.takaful.domain.Payment;
import com.muqmeen.takaful.domain.Product;
import com.muqmeen.takaful.domain.ProductCoverageItem;
import com.muqmeen.takaful.domain.Quotation;
import com.muqmeen.takaful.domain.StoredFile;
import com.muqmeen.takaful.repository.ConsultationApplicationRepository;
import com.muqmeen.takaful.repository.ContactInquiryRepository;
import com.muqmeen.takaful.repository.CustomerProfileRepository;
import com.muqmeen.takaful.repository.PaymentRepository;
import com.muqmeen.takaful.repository.ProductRepository;
import com.muqmeen.takaful.repository.SiteContentBlockRepository;
import com.muqmeen.takaful.repository.StoredFileRepository;
import com.muqmeen.takaful.service.ApplicationService;
import com.muqmeen.takaful.service.ContactEmailService;
import com.muqmeen.takaful.service.CustomerProfileService;
import com.muqmeen.takaful.service.CustomerProfileService.ProfileUpdate;
import com.muqmeen.takaful.service.CustomerService;
import com.muqmeen.takaful.service.PaymentService;
import com.muqmeen.takaful.service.ProductService;
import com.muqmeen.takaful.service.QuotationService;
import com.muqmeen.takaful.service.QuotationService.QuotationItemInput;
import com.muqmeen.takaful.service.chat.ChatKnowledgeBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "toyyibpay.mode=mock",
        "toyyibpay.secret-key=test-secret",
        "file-storage.mode=local",
        "file-storage.local-upload-dir=target/test-uploads"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DynamicApplicationIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private CustomerService customerService;
    @Autowired private ProductRepository productRepository;
    @Autowired private ConsultationApplicationRepository applicationRepository;
    @Autowired private ContactInquiryRepository contactInquiryRepository;
    @Autowired private CustomerProfileRepository customerProfileRepository;
    @Autowired private SiteContentBlockRepository siteContentBlockRepository;
    @Autowired private ApplicationService applicationService;
    @Autowired private CustomerProfileService customerProfileService;
    @Autowired private QuotationService quotationService;
    @Autowired private PaymentService paymentService;
    @Autowired private ProductService productService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private StoredFileRepository storedFileRepository;
    @Autowired private ChatKnowledgeBase chatKnowledgeBase;
    @MockitoBean private ContactEmailService contactEmailService;

    @BeforeEach
    void clean() {
        paymentRepository.deleteAll();
        applicationRepository.deleteAll();
        contactInquiryRepository.deleteAll();
        productRepository.deleteAll();
        siteContentBlockRepository.deleteAll();
        customerProfileRepository.deleteAll();
        storedFileRepository.deleteAll();
    }

    @Test
    void adminUploadsProductMediaAndPublicCanReadOnlyProductFiles() throws Exception {
        mockMvc.perform(multipart("/admin/products")
                        .file(image("productImage", "product.png"))
                        .file(new MockMultipartFile("documentFiles", "brochure.pdf", "application/pdf", new byte[] {5, 6, 7}))
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("name", "Media Product")
                        .param("categoryLabel", "Family")
                        .param("summary", "Uses uploaded media")
                        .param("detailedDescription", "Admin uploaded the product image and brochure.")
                        .param("iconClass", "fa-shield-halved")
                        .param("active", "true")
                        .param("benefitsText", "Stored product media")
                        .param("coverageText", "Coverage | Stored from admin form")
                        .param("requirementsText", "Completed application")
                        .param("documentsText", "External fact sheet | https://example.com/facts.pdf | PDF"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products"));

        Product product = productService.findById(productRepository.findByName("Media Product").orElseThrow().getId()).orElseThrow();
        assertTrue(product.getImageUrl().startsWith("/files/"));
        assertEquals(2, product.getDocuments().size());
        String uploadedDocumentUrl = product.getDocuments().stream()
                .filter(document -> document.getUrl().startsWith("/files/"))
                .findFirst()
                .orElseThrow()
                .getUrl();

        mockMvc.perform(get("/products/" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(product.getImageUrl())))
                .andExpect(content().string(containsString(uploadedDocumentUrl)));

        mockMvc.perform(get(product.getImageUrl()))
                .andExpect(status().isOk());
        mockMvc.perform(get(uploadedDocumentUrl))
                .andExpect(status().isOk());

        StoredFile privateFile = new StoredFile();
        privateFile.setPurpose(FilePurpose.PROFILE_PICTURE);
        privateFile.setOriginalFilename("private.png");
        privateFile.setContentType("image/png");
        privateFile.setFileSize(1);
        privateFile.setStorageProvider("local");
        privateFile.setStorageKey("private/missing.png");
        storedFileRepository.save(privateFile);

        mockMvc.perform(get("/files/" + privateFile.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanManageCustomerAccountAndDisableSignIn() throws Exception {
        Product product = saveProduct("Customer Flow Product");
        Customer customer = customer("managed-customer");
        customerProfileService.update(customer, new ProfileUpdate(
                "Customer home address",
                "Engineer",
                "Lead Engineer",
                "Dynamic Employer",
                "Workplace address",
                new BigDecimal("84000"),
                "Maybank",
                "1234567890",
                new BigDecimal("171"),
                new BigDecimal("72")
        ), null);
        ConsultationApplication application = submittedApplication(customer, product);

        mockMvc.perform(get("/admin/customers")
                        .with(user("admin").roles("ADMIN"))
                        .param("q", customer.getEmail()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(customer.getFullName())))
                .andExpect(content().string(containsString(customer.getEmail())))
                .andExpect(content().string(containsString("Active")));

        mockMvc.perform(get("/admin/customers/" + customer.getId())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Customer home address")))
                .andExpect(content().string(containsString("Customer Flow Product")))
                .andExpect(content().string(containsString(application.getStatus().name())));

        mockMvc.perform(post("/admin/customers/" + customer.getId() + "/status")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("active", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/customers/" + customer.getId()));
        assertFalse(customerService.findById(customer.getId()).orElseThrow().isActive());

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", customer.getEmail())
                        .param("password", "password123")
                        .param("redirect", "/account"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void adminCanUpdateSiteContentAndLandingReflectsDatabaseValue() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your Trusted Takaful Partner")));

        mockMvc.perform(post("/admin/content")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("content_hero.title", "Database Managed Takaful Portal")
                        .param("content_products.title", "Products from Supabase Records")
                        .param("content_chat.knowledge", "CUSTOM CHAT KNOWLEDGE: Applications store applicant snapshots in database rows."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/content"));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Database Managed Takaful Portal")))
                .andExpect(content().string(containsString("Products from Supabase Records")))
                .andExpect(content().string(not(containsString("Your Trusted Takaful Partner"))));
        assertTrue(chatKnowledgeBase.systemPrompt().contains("CUSTOM CHAT KNOWLEDGE"));
        assertTrue(chatKnowledgeBase.systemPrompt().contains("Applications store applicant snapshots in database rows."));
    }

    @Test
    void anonymousCanBrowseProductsButApplicationRequiresLogin() throws Exception {
        Product product = saveProduct("Dynamic Medical");

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dynamic Medical")))
                .andExpect(content().string(containsString("Active Plans")))
                .andExpect(content().string(not(containsString("30+"))))
                .andExpect(content().string(not(containsString("70+"))))
                .andExpect(content().string(not(containsString("Paid Tips"))))
                .andExpect(content().string(not(containsString("Our Client Reviews"))));

        mockMvc.perform(get("/products/" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hospital income benefit")));

        mockMvc.perform(get("/applications/new").param("productId", product.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void consultationRequestPersistsAndAdminCanWorkTheFunnel() throws Exception {
        mockMvc.perform(post("/contact")
                        .with(csrf())
                        .param("fullName", "Public Visitor")
                        .param("email", "visitor@example.com")
                        .param("phoneNumber", "60123456789")
                        .param("topic", "Family protection")
                        .param("preferredContact", "WhatsApp")
                        .param("message", "I want to understand which product fits my family."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/#contact"));

        verify(contactEmailService).send(any(ContactEmailService.ContactMessage.class));
        var inquiry = contactInquiryRepository.findTop5ByOrderByCreatedAtDesc().get(0);
        assertEquals("Public Visitor", inquiry.getFullName());
        assertEquals("NEW", inquiry.getStatus());
        assertEquals("Family protection", inquiry.getTopic());
        assertEquals("WhatsApp", inquiry.getPreferredContact());

        // The consultation appears in the admin management list.
        mockMvc.perform(get("/admin/consultations").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Public Visitor")));

        // Admin moves it through the funnel and saves an internal note.
        mockMvc.perform(post("/admin/consultations/" + inquiry.getId() + "/status")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("status", "CONTACTED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/consultations/" + inquiry.getId()));
        assertEquals("CONTACTED", contactInquiryRepository.findById(inquiry.getId()).orElseThrow().getStatus());

        mockMvc.perform(post("/admin/consultations/" + inquiry.getId() + "/notes")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("agentNotes", "Called, scheduling Tuesday."))
                .andExpect(status().is3xxRedirection());
        assertEquals("Called, scheduling Tuesday.",
                contactInquiryRepository.findById(inquiry.getId()).orElseThrow().getAgentNotes());
    }

    @Test
    void adminCreatesStructuredProductAndPublicPageReadsDatabaseRows() throws Exception {
        mockMvc.perform(post("/admin/products")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("name", "PruBSN Dynamic")
                        .param("categoryLabel", "Family Protection")
                        .param("summary", "Database summary")
                        .param("detailedDescription", "Database detailed description")
                        .param("eligibility", "Malaysian residents")
                        .param("coveragePurpose", "Family continuity")
                        .param("termsNotes", "Subject to underwriting")
                        .param("iconClass", "fa-shield-halved")
                        .param("active", "true")
                        .param("benefitsText", "Benefit one\nBenefit two")
                        .param("coverageText", "Hospital income benefit | Helps during admission")
                        .param("requirementsText", "IC front and back")
                        .param("documentsText", "English brochure | /brochures/example.pdf | Brochure"))
                .andExpect(status().is3xxRedirection());

        Product saved = productRepository.findByName("PruBSN Dynamic").orElseThrow();
        mockMvc.perform(get("/admin/products/" + saved.getId()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Managing this product")))
                .andExpect(content().string(containsString("Core Information")))
                .andExpect(content().string(containsString("Benefit one")))
                .andExpect(content().string(containsString("Hospital income benefit")))
                .andExpect(content().string(containsString("English brochure")));

        mockMvc.perform(get("/products/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Benefit one")))
                .andExpect(content().string(containsString("Hospital income benefit")))
                .andExpect(content().string(containsString("IC front and back")));

        mockMvc.perform(post("/admin/products/" + saved.getId())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("name", "PruBSN Dynamic Updated")
                        .param("categoryLabel", "Updated Protection")
                        .param("summary", "Updated database summary")
                        .param("detailedDescription", "Updated detailed description")
                        .param("eligibility", "Updated eligibility")
                        .param("coveragePurpose", "Updated family continuity")
                        .param("termsNotes", "Updated terms")
                        .param("iconClass", "fa-heart-pulse")
                        .param("active", "true")
                        .param("benefitsText", "Updated benefit")
                        .param("coverageText", "Updated hospital benefit | Updated admission note")
                        .param("requirementsText", "Updated IC requirement")
                        .param("documentsText", "Updated brochure | /brochures/updated.pdf | Brochure"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/products/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PruBSN Dynamic Updated")))
                .andExpect(content().string(containsString("Updated benefit")))
                .andExpect(content().string(not(containsString("Benefit one"))));

        mockMvc.perform(post("/admin/products/" + saved.getId() + "/delete")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("PruBSN Dynamic Updated"))));
        mockMvc.perform(get("/products/" + saved.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/#products"));
    }

    @Test
    void customerUpdatesProfileCreatesDraftSubmitsApplicationAndSeesItInAccount() throws Exception {
        Product product = saveProduct("PruBSN Application");
        Customer customer = customer("flow");
        Customer otherCustomer = customer("flow-other");

        mockMvc.perform(multipart("/account/profile")
                        .file(image("profilePicture", "profile.png"))
                        .with(user(customer.getEmail()).roles("USER"))
                        .with(csrf())
                        .param("fullName", "Flow Updated User")
                        .param("email", customer.getEmail())
                        .param("phoneNumber", "60199887766")
                        .param("homeAddress", "123 Jalan Takaful")
                        .param("occupation", "Teacher")
                        .param("positionTitle", "Senior Teacher")
                        .param("employerName", "School")
                        .param("workplaceAddress", "School Address")
                        .param("annualIncome", "60000")
                        .param("bankName", "Maybank")
                        .param("bankAccountNumber", "123456789")
                        .param("heightCm", "170")
                        .param("weightKg", "70"))
                .andExpect(status().is3xxRedirection());

        Customer updatedCustomer = customerService.findById(customer.getId()).orElseThrow();
        assertEquals("Flow Updated User", updatedCustomer.getFullName());
        assertEquals("60199887766", updatedCustomer.getPhoneNumber());

        CustomerProfile profile = customerProfileService.getOrCreate(customer);
        mockMvc.perform(get("/files/" + profile.getProfilePicture().getId()).with(user(customer.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[] {1, 2, 3, 4}));
        mockMvc.perform(get("/files/" + profile.getProfilePicture().getId()).with(user(otherCustomer.getEmail()).roles("USER")))
                .andExpect(status().isForbidden());

        ConsultationApplication draft = applicationService.start(customer, product.getId());
        mockMvc.perform(multipart("/applications/" + draft.getId())
                        .file(image("icFront", "front.png"))
                        .file(image("icBack", "back.png"))
                        .with(request -> { request.setMethod("POST"); return request; })
                        .with(user(customer.getEmail()).roles("USER"))
                        .with(csrf())
                        .param("action", "submit")
                        .param("signatureDataUrl", signature())
                        .param("applicantFullName", "Flow User")
                        .param("email", customer.getEmail())
                        .param("phoneNumber", "60123456789")
                        .param("homeAddress", "123 Jalan Takaful")
                        .param("occupation", "Teacher")
                        .param("positionTitle", "Senior Teacher")
                        .param("employerName", "School")
                        .param("workplaceAddress", "School Address")
                        .param("annualIncome", "60000")
                        .param("bankName", "Maybank")
                        .param("bankAccountNumber", "123456789")
                        .param("heightCm", "170")
                        .param("weightKg", "70")
                        .param("nominee1FullName", "Nominee User")
                        .param("nominee1IcNumber", "900101011111")
                        .param("nominee1Relationship", "Spouse")
                        .param("nominee1HomeAddress", "Nominee Address")
                        .param("nominee1Occupation", "Engineer")
                        .param("nominee1PhoneNumber", "60122222222")
                        .param("nominee2FullName", "Second Nominee")
                        .param("nominee2IcNumber", "910202022222")
                        .param("nominee2Relationship", "Child")
                        .param("nominee2HomeAddress", "Second Address")
                        .param("nominee2Occupation", "Student")
                        .param("nominee2PhoneNumber", "60133333333"))
                .andExpect(status().is3xxRedirection());

        ConsultationApplication submitted = applicationService.findById(draft.getId()).orElseThrow();
        assertEquals(2, submitted.getNominees().size());
        mockMvc.perform(get("/files/" + submitted.getIcFrontFile().getId()).with(user(customer.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[] {1, 2, 3, 4}));
        mockMvc.perform(get("/files/" + submitted.getIcFrontFile().getId()).with(user(otherCustomer.getEmail()).roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/files/" + submitted.getIcFrontFile().getId()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/applications/" + submitted.getId()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Uploaded Files")))
                .andExpect(content().string(containsString("IC_FRONT")))
                .andExpect(content().string(containsString("Storage key")));

        mockMvc.perform(get("/account").with(user(customer.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Flow Updated User")))
                .andExpect(content().string(containsString("PruBSN Application")))
                .andExpect(content().string(containsString("SUBMITTED")));

        mockMvc.perform(get("/applications/" + submitted.getId()).with(user(customer.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("What Happens Next")))
                .andExpect(content().string(containsString("Annual income")))
                .andExpect(content().string(containsString("Claim bank details")))
                .andExpect(content().string(containsString("Private Documents and Nominees")))
                .andExpect(content().string(containsString("Second Nominee")));
    }

    @Test
    void customerCannotReadAnotherCustomersApplication() throws Exception {
        Product product = saveProduct("Private Product");
        Customer owner = customer("owner");
        Customer other = customer("other");
        ConsultationApplication application = applicationService.start(owner, product.getId());

        mockMvc.perform(get("/applications/" + application.getId())
                        .with(user(other.getEmail()).roles("USER")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void customerCanSaveAndDeleteDraftButCannotEditSubmittedApplication() throws Exception {
        Product product = saveProduct("Draft Lifecycle Product");
        Customer customer = customer("draft-flow");
        ConsultationApplication draft = applicationService.start(customer, product.getId());

        mockMvc.perform(post("/applications/" + draft.getId())
                        .with(user(customer.getEmail()).roles("USER"))
                        .with(csrf())
                        .param("action", "draft")
                        .param("applicantFullName", "Draft Customer")
                        .param("email", customer.getEmail())
                        .param("phoneNumber", "60123456789")
                        .param("homeAddress", "Draft Home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/applications/" + draft.getId()));

        ConsultationApplication savedDraft = applicationService.findById(draft.getId()).orElseThrow();
        assertEquals(ApplicationStatus.DRAFT, savedDraft.getStatus());
        assertEquals("Draft Customer", savedDraft.getApplicantFullName());

        mockMvc.perform(get("/account").with(user(customer.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Draft Lifecycle Product")))
                .andExpect(content().string(containsString("DRAFT")));

        mockMvc.perform(post("/applications/" + draft.getId() + "/delete")
                        .with(user(customer.getEmail()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account"));
        assertFalse(applicationRepository.findById(draft.getId()).isPresent());

        ConsultationApplication submitted = submittedApplication(customer, product);
        mockMvc.perform(get("/applications/" + submitted.getId() + "/edit").with(user(customer.getEmail()).roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/applications/" + submitted.getId()));

        mockMvc.perform(post("/applications/" + submitted.getId())
                        .with(user(customer.getEmail()).roles("USER"))
                        .with(csrf())
                        .param("action", "draft")
                        .param("applicantFullName", "Should Not Change"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/applications/" + submitted.getId() + "/edit"));
        assertEquals("draft-flow User", applicationService.findById(submitted.getId()).orElseThrow().getApplicantFullName());
    }

    @Test
    void adminReviewsApplicationCreatesQuotationAndMockPaymentUpdatesStatus() throws Exception {
        Product product = saveProduct("Quoted Product");
        Customer customer = customer("quoted");
        Customer otherCustomer = customer("quoted-other");
        ConsultationApplication application = submittedApplication(customer, product);

        mockMvc.perform(get("/admin/dashboard").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Quoted Product")));

        mockMvc.perform(get("/admin/applications/" + application.getId()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Review steps")))
                .andExpect(content().string(containsString("Build a quotation after review")))
                .andExpect(content().string(containsString("Claim bank details")))
                .andExpect(content().string(containsString("Workplace address")));

        mockMvc.perform(post("/admin/applications/" + application.getId() + "/status")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("status", "NEEDS_INFO")
                        .param("adminReviewNotes", "Initial review completed")
                        .param("correctionRequest", "Please upload a clearer IC image and confirm bank details."))
                .andExpect(status().is3xxRedirection());

        ConsultationApplication needsInfo = applicationService.findById(application.getId()).orElseThrow();
        assertEquals(ApplicationStatus.NEEDS_INFO, needsInfo.getStatus());
        assertEquals("Please upload a clearer IC image and confirm bank details.", needsInfo.getCorrectionRequest());

        mockMvc.perform(get("/applications/" + application.getId()).with(user(customer.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Please upload a clearer IC image and confirm bank details.")))
                .andExpect(content().string(containsString("Edit and Resubmit")));

        mockMvc.perform(get("/applications/" + application.getId() + "/edit").with(user(customer.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Correction Request")))
                .andExpect(content().string(containsString("Complete the form")))
                .andExpect(content().string(containsString("Claim bank name")));

        mockMvc.perform(post("/admin/applications/" + application.getId() + "/status")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("status", "UNDER_REVIEW"))
                .andExpect(status().is3xxRedirection());

        Quotation quotation = quotationService.save(application, "Reviewed",
                List.of(new QuotationItemInput("Base contribution", "Monthly plan", new BigDecimal("120.00"), true)));
        quotation = quotationService.publish(quotation.getId());

        mockMvc.perform(post("/quotations/" + quotation.getId() + "/pay")
                        .with(user(otherCustomer.getEmail()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account"));

        var paymentStart = mockMvc.perform(post("/quotations/" + quotation.getId() + "/pay")
                        .with(user(customer.getEmail()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String redirect = paymentStart.getResponse().getRedirectedUrl();
        assertTrue(redirect.startsWith("/payment/mock/"));

        Payment payment = paymentRepository.findByQuotation(quotation);
        assertEquals("PENDING", payment.getStatus());
        assertEquals(ApplicationStatus.PAYMENT_PENDING, applicationService.findById(application.getId()).orElseThrow().getStatus());

        mockMvc.perform(get(redirect))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ToyyibPay Mock")));

        mockMvc.perform(post("/payment/callback")
                        .param("status", "1")
                        .param("order_id", payment.getExternalReferenceNo())
                        .param("refno", "TP-REF-1")
                        .param("billcode", payment.getBillCode())
                        .param("hash", "invalid"))
                .andExpect(status().isBadRequest());

        String validHash = paymentService.expectedHash("1", payment.getExternalReferenceNo(), "TP-REF-1");
        mockMvc.perform(post("/payment/callback")
                        .param("status", "1")
                        .param("order_id", payment.getExternalReferenceNo())
                        .param("refno", "TP-REF-1")
                        .param("billcode", payment.getBillCode())
                        .param("amount", "120.00")
                        .param("hash", validHash))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        Payment paidPayment = paymentRepository.findByQuotation(quotation);
        assertEquals("PAID", paidPayment.getStatus());
        assertEquals(ApplicationStatus.PAID, applicationService.findById(application.getId()).orElseThrow().getStatus());
    }

    @Test
    void adminRoutesRemainAdminOnly() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin/dashboard").with(user("user@example.com").roles("USER")))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin/dashboard").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void adminLinkOnlyShownToAdmins() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(content().string(not(containsString("href=\"/admin/dashboard\""))));
        mockMvc.perform(get("/").with(user("user@example.com").roles("USER")))
                .andExpect(content().string(not(containsString("href=\"/admin/dashboard\""))));
        mockMvc.perform(get("/").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(containsString("href=\"/admin/dashboard\"")));
    }

    private Product saveProduct(String name) {
        Product product = new Product();
        product.setName(name);
        product.setCategoryLabel("Medical");
        product.setSummary("Dynamic product summary");
        product.setDescription("Dynamic product description");
        product.setDetailedDescription("Full dynamic product detail");
        product.setIconClass("fa-heart-pulse");
        product.setActive(true);
        product.setFeatured(true);
        ProductCoverageItem coverageItem = new ProductCoverageItem();
        coverageItem.setProduct(product);
        coverageItem.setItemName("Hospital income benefit");
        coverageItem.setItemDescription("Helps during admission");
        product.getCoverageItems().add(coverageItem);
        return productRepository.save(product);
    }

    private Customer customer(String prefix) {
        return customerService.register(prefix + " User", prefix + "." + UUID.randomUUID() + "@example.com", "60123456789", "password123");
    }

    private ConsultationApplication submittedApplication(Customer customer, Product product) {
        ConsultationApplication application = applicationService.start(customer, product.getId());
        application.setApplicantFullName(customer.getFullName());
        application.setEmail(customer.getEmail());
        application.setPhoneNumber(customer.getPhoneNumber());
        application.setHomeAddress("Address");
        application.setOccupation("Teacher");
        application.setPositionTitle("Senior Teacher");
        application.setEmployerName("School");
        application.setWorkplaceAddress("Workplace");
        application.setAnnualIncome(new BigDecimal("60000"));
        application.setBankName("Maybank");
        application.setBankAccountNumber("123456789");
        application.setHeightCm(new BigDecimal("170"));
        application.setWeightKg(new BigDecimal("70"));
        application.setStatus(ApplicationStatus.SUBMITTED);
        return applicationRepository.save(application);
    }

    private MockMultipartFile image(String name, String filename) {
        return new MockMultipartFile(name, filename, "image/png", new byte[] {1, 2, 3, 4});
    }

    private String signature() {
        return "data:image/png;base64,iVBORw0KGgo=";
    }
}
