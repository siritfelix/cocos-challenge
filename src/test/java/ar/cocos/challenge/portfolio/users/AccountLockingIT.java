package ar.cocos.challenge.portfolio.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import ar.cocos.challenge.portfolio.TestcontainersConfiguration;
import ar.cocos.challenge.portfolio.users.domain.Account;
import ar.cocos.challenge.portfolio.users.domain.AccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Verifica que el bloqueo pesimista de la fila de la cuenta serializa a las
 * operaciones concurrentes de una misma cuenta.
 *
 * <p>Es la garantia central de concurrencia del sistema (spec FR-094, D-022):
 * mientras una transaccion tiene tomado el lock de la cuenta, otra no puede
 * obtenerlo, de modo que no pueden consumir los mismos recursos.</p>
 *
 * <p>Cada hilo usa su propia transaccion (mediante {@link TransactionTemplate})
 * porque la anotacion {@code @Transactional} de test abraza el metodo completo
 * y no permitiria observar el bloqueo desde otro hilo.</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AccountLockingIT {

	/**
	 * Tiempo que el primer hilo mantiene el lock antes de liberarlo.
	 */
	private static final long HOLD_MILLIS = 1500;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	@DisplayName("Una segunda transaccion no puede tomar el lock de la cuenta mientras la primera lo tiene")
	void secondTransactionWaitsForTheFirstToCommit() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch firstHasLock = new CountDownLatch(1);
		CountDownLatch secondAttemptedLock = new CountDownLatch(1);
		AtomicBoolean secondAcquiredLock = new AtomicBoolean(false);

		try {
			// Hilo 1: toma el lock de la cuenta y lo mantiene durante HOLD_MILLIS.
			Future<Account> first = executor.submit(() -> inTransaction(() -> {
				Account account = accountRepository.findByAccountNumberForUpdate("10001").orElseThrow();
				firstHasLock.countDown();
				sleep(HOLD_MILLIS);
				return account;
			}));

			assertThat(firstHasLock.await(30, TimeUnit.SECONDS)).isTrue();

			// Hilo 2: intenta tomar el lock mientras el hilo 1 lo tiene. Debe
			// quedar en espera hasta que el hilo 1 confirme la transaccion.
			Future<Account> second = executor.submit(() -> inTransaction(() -> {
				secondAttemptedLock.countDown();
				Account account = accountRepository.findByAccountNumberForUpdate("10001").orElseThrow();
				secondAcquiredLock.set(true);
				return account;
			}));

			assertThat(secondAttemptedLock.await(30, TimeUnit.SECONDS)).isTrue();

			// Mientras el hilo 1 mantiene el lock, el hilo 2 no debe haberlo
			// conseguido aunque ya haya intentado tomarlo.
			sleep(HOLD_MILLIS / 2);
			assertThat(secondAcquiredLock)
					.as("el segundo hilo no debe obtener el lock hasta que el primero confirme")
					.isFalse();

			Account secondAccount = second.get(30, TimeUnit.SECONDS);

			assertThat(secondAcquiredLock).isTrue();
			assertThat(secondAccount.accountNumber()).isEqualTo("10001");
			assertThat(first.get(30, TimeUnit.SECONDS)).isNotNull();
		} finally {
			executor.shutdownNow();
		}
	}

	private <T> T inTransaction(java.util.function.Supplier<T> action) {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		return template.execute(status -> action.get());
	}

	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while holding the account lock", exception);
		}
	}

}
