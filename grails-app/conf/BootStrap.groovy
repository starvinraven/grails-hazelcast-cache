class BootStrap {

	def grailsApplication
	def cacheService
		
    def init = { servletContext ->
		cacheService.checkCache("foo", "bar") {
			"yay"
		}
    }
    def destroy = {
    }
	
}
