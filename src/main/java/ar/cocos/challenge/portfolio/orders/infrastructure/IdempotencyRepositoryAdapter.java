package ar.cocos.challenge.portfolio.orders.infrastructure;

import java.util.Optional;

import ar.cocos.challenge.portfolio.orders.domain.IdempotencyKey;
import ar.cocos.challenge.portfolio.orders.domain.IdempotencyRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador del puerto {@link IdempotencyRepository} sobre Spring Data JPA.
 */
@Repository
public class IdempotencyRepositoryAdapter implements IdempotencyRepository {

	private final JpaIdempotencyRepository jpaIdempotencyRepository;

	/**
	 * @param jpaIdempotencyRepository repositorio Spring Data
	 */
	public IdempotencyRepositoryAdapter(JpaIdempotencyRepository jpaIdempotencyRepository) {
		this.jpaIdempotencyRepository = jpaIdempotencyRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<IdempotencyKey> findByAccountIdAndKey(Long accountId, String key) {
		return jpaIdempotencyRepository.findByUserIdAndIdempotencyKey(accountId, key).map(this::toDomain);
	}

	@Override
	@Transactional
	public IdempotencyKey save(IdempotencyKey idempotencyKey) {
		IdempotencyKeyEntity entity = new IdempotencyKeyEntity();
		entity.setUserId(idempotencyKey.accountId());
		entity.setKey(idempotencyKey.key());
		entity.setRequestHash(idempotencyKey.requestHash());
		entity.setOrderId(idempotencyKey.orderId());
		entity.setCreatedAt(idempotencyKey.createdAt());
		IdempotencyKeyEntity saved = jpaIdempotencyRepository.save(entity);
		return new IdempotencyKey(saved.getId(), saved.getUserId(), saved.getKey(), saved.getRequestHash(),
				saved.getOrderId(), saved.getCreatedAt());
	}

	private IdempotencyKey toDomain(IdempotencyKeyEntity entity) {
		return new IdempotencyKey(entity.getId(), entity.getUserId(), entity.getKey(), entity.getRequestHash(),
				entity.getOrderId(), entity.getCreatedAt());
	}

}
