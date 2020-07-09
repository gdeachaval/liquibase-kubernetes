package liquibase.lockservice.ext.ext;

import com.github.patricio78.liquibase.kubernetes.KubernetesConnector;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.exception.LockException;
import liquibase.executor.ExecutorService;
import liquibase.lockservice.StandardLockService;
import liquibase.statement.core.SelectFromDatabaseChangeLogLockStatement;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;

import java.util.StringTokenizer;

public class KubernetesLockService extends StandardLockService {

    private static final Log LOG = org.apache.commons.logging.LogFactory.getLog(KubernetesLockService.class);

    @Override
    public int getPriority() {
        return 1000;
    }

    @Override
    public boolean supports(Database database) {
        return KubernetesConnector.getInstance().isConnected();
    }

    @Override
    public void waitForLock() throws LockException {
        try {
            String lockedBy = ExecutorService.getInstance().getExecutor(database).queryForObject(new SelectFromDatabaseChangeLogLockStatement("LOCKEDBY"), String.class);
            if (StringUtils.isNotBlank(lockedBy)) {
                LOG.trace("Database locked by: " + lockedBy);
                StringTokenizer tok = new StringTokenizer(lockedBy, ":");
                if (tok.countTokens() == 2) {
                    String podNamespace = tok.nextToken();
                    String podName = tok.nextToken();
                    if (KubernetesConnector.getInstance().isCurrentPod(podNamespace, podName)) {
                        LOG.debug("Lock created by the same pod, release lock");
                        releaseLock();
                    }
                    Boolean lockHolderPodActive = KubernetesConnector.getInstance().isPodActive(podNamespace, podName);
                    if (lockHolderPodActive != null && !lockHolderPodActive) {
                        LOG.debug("Lock created by an inactive pod, release lock");
                        releaseLock();
                    }
                } else {
                    LOG.debug("Can't parse LOCKEDBY field: " + lockedBy);
                }
            } else {
                LOG.trace("Databased is not locked");
            }
        } catch (DatabaseException e) {
            LOG.error("Can't read the LOCKEDBY field from databasechangeloglock", e);
        }
        super.waitForLock();
    }
}


