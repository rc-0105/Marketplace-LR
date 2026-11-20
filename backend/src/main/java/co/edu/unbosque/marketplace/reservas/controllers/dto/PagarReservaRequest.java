package co.edu.unbosque.marketplace.reservas.controllers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Pago de una reserva con un método de prueba de Stripe (SOF-34). Solo se aceptan las tarjetas de prueba, así
 * nunca viaja un dato de tarjeta real.
 */
public record PagarReservaRequest(
        @NotBlank(message = "Indica el método de pago de prueba")
        @Pattern(regexp = "pm_card_visa|pm_card_mastercard|pm_card_chargeDeclined|pm_card_chargeDeclinedInsufficientFunds",
                message = "Usa una tarjeta de prueba: pm_card_visa, pm_card_mastercard, pm_card_chargeDeclined o "
                        + "pm_card_chargeDeclinedInsufficientFunds")
        String metodoPago) {
}
