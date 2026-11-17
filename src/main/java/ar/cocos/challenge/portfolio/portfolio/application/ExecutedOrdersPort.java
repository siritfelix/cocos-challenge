package ar.cocos.challenge.portfolio.portfolio.application;

import java.util.List;

import ar.cocos.challenge.portfolio.portfolio.domain.CashSummary;
import ar.cocos.challenge.portfolio.portfolio.domain.ExecutedTrade;
import ar.cocos.challenge.portfolio.portfolio.domain.InstrumentHolding;
import ar.cocos.challenge.portfolio.users.domain.Account;
/**
 * Puerto de lectura de las ordenes de la cuenta para el portfolio.
 *
 * <p>El contexto de portfolio NO accede al repositorio de ordenes: consume un
 * puerto propio, implementado por un adaptador en la infraestructura de
 * portfolio (Principio V).</p>
 */
public interface ExecutedOrdersPort {

	/**
	 * Devuelve el resumen del efectivo de la cuenta.
	 *
	 * @param account contexto de la cuenta
	 * @return el efectivo total, reservado y disponible
	 */
	CashSummary cashSummary(Account account);

	/**
	 * Devuelve las cantidades de acciones de la cuenta en cada instrumento con
	 * posicion.
	 *
	 * @param account contexto de la cuenta
	 * @return las posiciones con cantidad
	 */
	List<InstrumentHolding> holdings(Account account);

	/**
	 * Devuelve las ordenes ejecutadas de un par (cuenta, instrumento) en orden
	 * cronologico, que son la entrada del fold del costo promedio.
	 *
	 * @param account contexto de la cuenta
	 * @param instrumentId identificador del instrumento
	 * @return las ordenes de compra y venta ejecutadas, ordenadas por fecha y
	 *         luego por identificador
	 */
	List<ExecutedTrade> executedTrades(Account account, Long instrumentId);

}
