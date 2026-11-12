package ar.cocos.challenge.portfolio.instruments.domain;

/**
 * Instrumento del catalogo junto con su cotizacion mas reciente.
 *
 * <p>Es el resultado de la consulta del catalogo: la cotizacion puede ser
 * {@code null} cuando el instrumento no tiene ninguna registrada (spec 002,
 * FR-002). El precio y el cierre anterior de la respuesta se derivan de este
 * par segun las reglas de {@code LatestQuote.isAvailable()} y
 * {@code hasPreviousClose()}.</p>
 *
 * @param instrument instrumento del catalogo
 * @param quote cotizacion mas reciente del instrumento, o {@code null} si no
 *        tiene ninguna
 */
public record QuotedInstrument(Instrument instrument, LatestQuote quote) {

}
