package com.muqmeen.takaful.service;

import com.muqmeen.takaful.domain.ApplicationStatus;
import com.muqmeen.takaful.domain.ConsultationApplication;
import com.muqmeen.takaful.repository.PaymentRepository;
import com.muqmeen.takaful.repository.PlanFinderResponseRepository;
import com.muqmeen.takaful.repository.QuotationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Real figures for the admin dashboard. Everything here is computed from stored data — when there
 * is nothing to compare against (e.g. no payments last month) the template simply hides the hint
 * instead of showing an invented number.
 */
@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final List<ApplicationStatus> TO_REVIEW = List.of(ApplicationStatus.SUBMITTED, ApplicationStatus.UNDER_REVIEW);
    private static final List<ApplicationStatus> AWAITING_PAYMENT = List.of(ApplicationStatus.QUOTED, ApplicationStatus.PAYMENT_PENDING);
    private static final int OVERDUE_DAYS = 2;

    private final QuotationRepository quotationRepository;
    private final PaymentRepository paymentRepository;
    private final PlanFinderResponseRepository planFinderResponseRepository;

    public AdminDashboardService(QuotationRepository quotationRepository,
                                 PaymentRepository paymentRepository,
                                 PlanFinderResponseRepository planFinderResponseRepository) {
        this.quotationRepository = quotationRepository;
        this.paymentRepository = paymentRepository;
        this.planFinderResponseRepository = planFinderResponseRepository;
    }

    public DashboardStats stats(List<ConsultationApplication> applications) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime overdueBefore = now.minusDays(OVERDUE_DAYS);

        long toReview = 0;
        long overdue = 0;
        long awaitingPayment = 0;
        for (ConsultationApplication app : applications) {
            if (TO_REVIEW.contains(app.getStatus())) {
                toReview++;
                if (receivedAt(app).isBefore(overdueBefore)) {
                    overdue++;
                }
            } else if (AWAITING_PAYMENT.contains(app.getStatus())) {
                awaitingPayment++;
            }
        }
        BigDecimal pendingAmount = quotationRepository.sumSelectedAmountForApplicationStatuses(AWAITING_PAYMENT);

        LocalDateTime monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
        LocalDateTime lastMonthStart = monthStart.minusMonths(1);
        BigDecimal paidThisMonth = ringgit(paymentRepository.sumPaidCentsBetween(monthStart, monthStart.plusMonths(1)));
        BigDecimal paidLastMonth = ringgit(paymentRepository.sumPaidCentsBetween(lastMonthStart, monthStart));
        Integer paidChangePercent = paidLastMonth.signum() > 0
                ? paidThisMonth.subtract(paidLastMonth).multiply(BigDecimal.valueOf(100))
                        .divide(paidLastMonth, 0, RoundingMode.HALF_UP).intValue()
                : null;
        String lastMonthName = lastMonthStart.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        long planFinderUsed = planFinderResponseRepository.count();
        long planFinderConverted = planFinderResponseRepository.countConverted();
        Set<Long> planFinderApplicationIds = new HashSet<>(planFinderResponseRepository.findLinkedApplicationIds());

        return new DashboardStats(toReview, overdue, awaitingPayment, pendingAmount == null ? BigDecimal.ZERO : pendingAmount,
                paidThisMonth, paidChangePercent, lastMonthName, planFinderUsed, planFinderConverted,
                planFinderApplicationIds, lastSevenDays(applications, now.toLocalDate()));
    }

    /** Applications received on each of the last 7 days, oldest first; the last entry is today. */
    private List<DayCount> lastSevenDays(List<ConsultationApplication> applications, LocalDate today) {
        long[] counts = new long[7];
        LocalDate first = today.minusDays(6);
        for (ConsultationApplication app : applications) {
            LocalDate day = receivedAt(app).toLocalDate();
            if (!day.isBefore(first) && !day.isAfter(today)) {
                counts[(int) (day.toEpochDay() - first.toEpochDay())]++;
            }
        }
        long max = 0;
        for (long c : counts) {
            max = Math.max(max, c);
        }
        List<DayCount> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = first.plusDays(i);
            int percent = max == 0 ? 0 : (int) Math.round(counts[i] * 100.0 / max);
            days.add(new DayCount(day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    day, counts[i], percent, day.equals(today)));
        }
        return days;
    }

    private static LocalDateTime receivedAt(ConsultationApplication app) {
        if (app.getSubmittedAt() != null) {
            return app.getSubmittedAt();
        }
        return app.getCreatedAt() != null ? app.getCreatedAt() : LocalDate.now().atTime(LocalTime.MIDNIGHT);
    }

    private static BigDecimal ringgit(long cents) {
        return BigDecimal.valueOf(cents).movePointLeft(2);
    }

    public record DayCount(String label, LocalDate date, long count, int percent, boolean today) {
    }

    public record DashboardStats(long toReview,
                                 long overdueReviews,
                                 long awaitingPayment,
                                 BigDecimal pendingAmount,
                                 BigDecimal paidThisMonth,
                                 Integer paidChangePercent,
                                 String lastMonthName,
                                 long planFinderUsed,
                                 long planFinderConverted,
                                 Set<Long> planFinderApplicationIds,
                                 List<DayCount> week) {
        public long weekTotal() {
            return week.stream().mapToLong(DayCount::count).sum();
        }
    }
}
