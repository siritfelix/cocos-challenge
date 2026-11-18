package ar.cocos.challenge.portfolio.instruments.domain;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de acceso al catalogo de instrumentos.
 */
public interface InstrumentRepository {

	/**
	 * Busca un instrumento por su identificador interno.
	 *
	 * @param instrumentId identificador interno del instrumento
	 * @return el instrumento, o vacio si no existe
	 */
	Optional<Instrument> findById(Long instrumentId);

	/**
	 * Busca un instrumento por su ticker.
	 *
	 * @param ticker simbolo del instrumento
	 * @return el instrumento, o vacio si no existe
	 */
	Optional<Instrument> findByTicker(String ticker);

	/**
	 * Devuelve todos los instrumentos del catalogo.
	 *
	 * @return la lista de instrumentos
	 */
	List<Instrument> findAll();

	/**
	 * Busca instrumentos por coincidencia parcial de ticker y/o de nombre, sin
	 * distinguir mayusculas de minusculas.
	 *
	 * @param query texto de busqueda
	 * @return los instrumentos que coinciden, sin duplicados
	 */
	List<Instrument> search(String query);

	/**
	 * Busca una lista instrumento por su identificadores interno.
	 *
	 * @param ids identificadores internos del instrumento
	 * @return el instrumento, o vacio si no existe
	 */
	List<Instrument> findAllById(List<Long> ids);

}
