package hazelcast

import org.springframework.beans.factory.InitializingBean
import com.hazelcast.core.Hazelcast
import java.util.concurrent.locks.Lock

class CacheService implements InitializingBean {

	def caches = [:]
	def grailsApplication
	static transactional = false
	
	void afterPropertiesSet() {
		def configEnum = grailsApplication.config.hazelcast.cache.maps
		configEnum?.each {
			caches[it] = Hazelcast.getMap(it.name())
		}
		log.info("Initialized ${caches?.size() ?: 0} cache maps")
	}
	
    def checkCache(def cache, Serializable key, int lockTries = 10, int blockMillis = 200, Closure dataClosure) {
		def val = caches[cache].get(key)
		def cacheId = cache.name()+"-"+key
		int i = 0
		while(val == null && i < lockTries) {
			Lock lock = Hazelcast.getLock(cacheId)
			if(lock.tryLock()) {
				try {
					Long dataLoadStart = System.currentTimeMillis()
					try {
						val = dataClosure()
					} catch(Exception e) {
						throw new RuntimeException("Error executing cache closure: ${e.getMessage()} for key ${cacheId}", e)
					}
					Long dataLoadTime = System.currentTimeMillis() - dataLoadStart
					log.info("Loaded data for ${cacheId} in ${dataLoadTime} ms")
					if(val || val == [] || val == [:]) {
						caches[cache].putAt(key, val)
					} else {
						log.warn("Got no data for cache: ${cacheId}")
						return null
					}
				} finally {
					lock.unlock()
				}
			} else {
				log.debug("Could not acquire lock: ${cacheId}, attempt ${i+1}")
				Thread.sleep(blockMillis)
				val = caches[cache].get(key)
			}
			i++
		}
		return deepcopy(val)
    }
	
	def deepcopy(orig) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream()
		ObjectOutputStream oos = new ObjectOutputStream(bos)
		oos.writeObject(orig)
		oos.flush()
		ByteArrayInputStream bin = new ByteArrayInputStream(bos.toByteArray())
		ObjectInputStream ois = new ObjectInputStream(bin)
		return ois.readObject()
	}
}