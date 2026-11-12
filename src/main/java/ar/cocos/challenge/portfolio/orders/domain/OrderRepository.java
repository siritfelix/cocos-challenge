package ar.cocos.challenge.portfolio.orders.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;


/**
 * Puerto de acceso al ledger de ordenes.
 *
 * <p>Los saldos y las posiciones no se persisten: se derivan de las ordenes
 * registradas mediante las consultas agregadas de este puerto (spec D-047).</p>
 */
public interface OrderRepository {

	/**
	 * Persiste una orden.
	 *
	 * @param order orden a persistir
	 * @return la orden persistida, con su identificador asignado
	 */
	Order save(Order order);

	/**
	 * Busca una orden por su identificador.
	 *
	 * @param orderId identificador de la orden
	 * @return la orden, o vacio si no existe
	 */
	Optional<Order> findById(Long orderId);

	/**
	 * Busca una orden que pertenezca a la cuenta indicada.
	 *
	 * <p>El filtro por cuenta evita que una orden de otra cuenta sea visible o
	 * modificable (spec FR-004, FR-065).</p>
	 *
	 * @param orderId identificador de la orden
	 * @param accountId identificador de la cuenta propietaria
	 * @return la orden, o vacio si no existe o no pertenece a la cuenta
	 */
	Optional<Order> findByIdAndAccountId(Long orderId, Long accountId);

	/**
	 * Calcula el efectivo total de la cuenta, derivado de las ordenes
	 * ejecutadas.
	 *
	 * <p>Suma los ingresos y ventas, y resta los retiros y las compras
	 * (spec BR-008).</p>
	 *
	 * @param accountId identificador de la cuenta
	 * @return el efectivo total
	 */
	BigDecimal sumFilledCash(Long accountId);

	/**
	 * Calcula el efectivo reservado por las compras limite pendientes.
	 *
	 * @param accountId identificador de la cuenta
	 * @return el importe reservado
	 */
	BigDecimal sumReservedCash(Long accountId);

	/**
	 * Calcula la cantidad de acciones netas de un instrumento, derivada de las
	 * compras y ventas ejecutadas.
	 *
	 * @param accountId identificador de la cuenta
	 * @param instrumentId identificador del instrumento
	 * @return la cantidad neta
	 */
	BigDecimal sumFilledShares(Long accountId, Long instrumentId);

	/**
	 * Calcula la cantidad de acciones reservadas por las ventas limite
	 * pendientes de un instrumento.
	 *
	 * @param accountId identificador de la cuenta
	 * @param instrumentId identificador del instrumento
	 * @return la cantidad reservada
	 */
	BigDecimal sumReservedShares(Long accountId, Long instrumentId);

	/**
	 * Devuelve los identificadores de los instrumentos en los que la cuenta
	 * tiene alguna operacion sobre acciones.
	 *
	 * <p>Permite listar las posiciones del portfolio sin recorrer el catalogo
	 * completo de instrumentos (spec FR-069, FR-079).</p>
	 *
	 * @param accountId identificador de la cuenta
	 * @return los identificadores de los instrumentos con posicion
	 */
	List<Long> findInstrumentsWithPosition(Long accountId);

	/**
	 * Busca la ultima orden de la cuenta para un instrumento, usada para
	 * derivar el costo promedio ponderado.
	 *
	 * @param accountId identificador de la cuenta
	 * @param instrumentId identificador del instrumento
	 * @return las ordenes ejecutadas del par, ordenadas por fecha ascendente
	 */
	List<Order> findExecutedTradesInChronologicalOrder(Long accountId, Long instrumentId);

}
