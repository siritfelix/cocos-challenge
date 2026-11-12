package ar.cocos.challenge.portfolio.orders.infrastructure;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import ar.cocos.challenge.portfolio.orders.domain.OrderSide;
import ar.cocos.challenge.portfolio.orders.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio Spring Data de la tabla {@code orders}.
 *
 * <p>Las consultas agregadas que calculan saldos y posiciones están en SQL
 * nativo: el {@code FILTER} de PostgreSQL permite expressar cada componente
 * del calculo en una sola sentencia, y el {@code COALESCE} evita el
 * {@code NULL} que {@code SUM} devuelve sobre un conjunto vacio (research R-008).</p>
 */
public interface JpaOrderRepository extends JpaRepository<OrderEntity, Long> {

	/**
	 * Busca una orden por su identificador.
	 *
	 * @param orderId identificador de la orden
	 * @return la entidad de la orden, o vacio si no existe
	 */
	Optional<OrderEntity> findById(Long orderId);

	/**
	 * Calcula el efectivo total de la cuenta a partir de las ordenes
	 * ejecutadas.
	 *
	 * <p>Los ingresos y las ventas suman; los retiros y las compras restan. Las
	 * transferencias aportan su importe (columna {@code size}) y las
	 * transacciones aportan cantidad por precio (spec BR-008).</p>
	 *
	 * @param accountId identificador de la cuenta
	 * @return el efectivo total
	 */
	@Query(value = """
			SELECT COALESCE(SUM(
			    CASE
			        WHEN o.side = 'CASH_IN' THEN o.size
			        WHEN o.side = 'CASH_OUT' THEN -o.size
			        WHEN o.side = 'BUY' THEN -(o.size * o.price)
			        WHEN o.side = 'SELL' THEN (o.size * o.price)
			        ELSE 0
			    END), 0)
			FROM orders o
			WHERE o.user_id = :accountId AND o.status = 'FILLED'
			""", nativeQuery = true)
	BigDecimal sumFilledCash(@Param("accountId") Long accountId);

	/**
	 * Calcula el efectivo reservado por las compras limite pendientes.
	 *
	 * @param accountId identificador de la cuenta
	 * @return el importe reservado
	 */
	@Query(value = """
			SELECT COALESCE(SUM(o.size * o.price), 0)
			FROM orders o
			WHERE o.user_id = :accountId AND o.status = 'NEW' AND o.side = 'BUY'
			""", nativeQuery = true)
	BigDecimal sumReservedCash(@Param("accountId") Long accountId);

	/**
	 * Calcula la cantidad neta de acciones de un instrumento a partir de las
	 * compras y ventas ejecutadas.
	 *
	 * @param accountId identificador de la cuenta
	 * @param instrumentId identificador del instrumento
	 * @return la cantidad neta
	 */
	@Query(value = """
			SELECT COALESCE(SUM(CASE WHEN o.side = 'BUY' THEN o.size ELSE -o.size END), 0)
			FROM orders o
			WHERE o.user_id = :accountId
			  AND o.instrument_id = :instrumentId
			  AND o.status = 'FILLED'
			""", nativeQuery = true)
	BigDecimal sumFilledShares(@Param("accountId") Long accountId, @Param("instrumentId") Long instrumentId);

	/**
	 * Calcula la cantidad de acciones reservadas por las ventas limite
	 * pendientes de un instrumento.
	 *
	 * @param accountId identificador de la cuenta
	 * @param instrumentId identificador del instrumento
	 * @return la cantidad reservada
	 */
	@Query(value = """
			SELECT COALESCE(SUM(o.size), 0)
			FROM orders o
			WHERE o.user_id = :accountId
			  AND o.instrument_id = :instrumentId
			  AND o.status = 'NEW'
			  AND o.side = 'SELL'
			""", nativeQuery = true)
	BigDecimal sumReservedShares(@Param("accountId") Long accountId, @Param("instrumentId") Long instrumentId);

	/**
	 * Devuelve los instrumentos en los que la cuenta tiene posicion.
	 *
	 * <p>Se consideran las operaciones sobre acciones: las transferencias de
	 * efectivo no generan posicion (spec FR-053). Se excluyen los instrumentos
	 * cuya cantidad neta es cero, que no constituyen una posicion (spec
	 * FR-079).</p>
	 *
	 * @param accountId identificador de la cuenta
	 * @return los identificadores de los instrumentos con posicion
	 */
	@Query(value = """
			SELECT o.instrument_id
			FROM orders o
			WHERE o.user_id = :accountId
			  AND o.side IN ('BUY', 'SELL')
			GROUP BY o.instrument_id
			HAVING COALESCE(SUM(
			    CASE WHEN o.side = 'BUY' THEN o.size ELSE -o.size END
			    ), 0) <> 0
			""", nativeQuery = true)
	List<Long> findInstrumentsWithPosition(@Param("accountId") Long accountId);

	/**
	 * Devuelve una pagina del historial de ordenes de la cuenta, con los filtros
	 * opcionales aplicados.
	 *
	 * <p>La consulta se restringe SIEMPRE a la cuenta identificada, con
	 * independencia de los filtros (spec FR-087). El filtro por ticker es una
	 * coincidencia exacta (spec FR-085).</p>
	 *
	 * <p>El desempate por identificador descendente garantiza un orden estable
	 * entre ordenes con la misma fecha, de modo que la paginacion no repita ni
	 * omita registros.</p>
	 *
	 * @param accountId identificador de la cuenta
	 * @param status estado por el que filtrar, o {@code null}
	 * @param side lado por el que filtrar, o {@code null}
	 * @param ticker ticker exacto por el que filtrar, o {@code null}
	 * @param pageable paginacion solicitada
	 * @return la pagina de ordenes
	 */
	@Query("""
			select o from OrderEntity o
			where o.userId = :accountId
			  and (:status is null or o.status = :status)
			  and (:side is null or o.side = :side)
			  and (:ticker is null or o.instrumentId in (
			      select i.id from InstrumentEntity i where i.ticker = :ticker))
			order by o.datetime desc, o.id desc
			""")
	Page<OrderEntity> findHistory(@Param("accountId") Long accountId, @Param("status") OrderStatus status,
			@Param("side") OrderSide side, @Param("ticker") String ticker, Pageable pageable);

	/**
	 * Devuelve las ordenes ejecutadas de un par (cuenta, instrumento) ordenadas
	 * de forma cronologica.
	 *
	 * <p>El desempate por identificador es esencial: el calculo del costo
	 * promedio ponderado es un fold secuencial y dos lecturas concurrentes
	 * deben obtener la misma secuencia (research R-009).</p>
	 *
	 * @param accountId identificador de la cuenta
	 * @param instrumentId identificador del instrumento
	 * @return las ordenes de compra y venta ejecutadas, ordenadas por fecha y
	 *         luego por identificador
	 */
	@Query("""
			select o from OrderEntity o
			where o.userId = :accountId
			  and o.instrumentId = :instrumentId
			  and o.status = 'FILLED'
			  and o.side in :sides
			order by o.datetime asc, o.id asc
			""")
	List<OrderEntity> findExecutedTradesInChronologicalOrder(@Param("accountId") Long accountId,
			@Param("instrumentId") Long instrumentId, @Param("sides") List<OrderSide> sides);

}
