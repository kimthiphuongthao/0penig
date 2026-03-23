import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise

def logError = { message, error ->
    if (binding.hasVariable('logger')) {
        logger.error(message, error)
    } else {
        println("${message}: ${error}")
    }
}

def escapeForJs = { value ->
    ((value ?: '') as String)
        .replace('\\', '\\\\')
        .replace("'", "\\'")
        .replace('"', '\\"')
        .replace('<', '\\x3c')
        .replace('>', '\\x3e')
        .replace('/', '\\/')
}

try {
    def accessToken = session['jellyfin_token'] as String
    def userId = session['jellyfin_user_id'] as String
    def deviceId = session['jellyfin_device_id'] as String
    def serverId = (System.getenv('JELLYFIN_SERVER_ID') ?: '8a4467ecf1d4422583f472d90cb8c78f') as String
    def requestUri = request?.uri?.toString() ?: ''
    def hostHeader = request.headers.getFirst('Host') as String
    def serverAddress = 'http://' + (hostHeader ?: 'jellyfin-b.sso.local:9080')

    return next.handle(context, request).then({ response ->
        try {
            def contentType = response?.headers?.getFirst('Content-Type')?.toLowerCase()
            if (!contentType?.contains('text/html')) {
                return response
            }

            if (!accessToken || !userId) {
                return response
            }

            def body = response?.entity?.getString()
            if (!body) {
                return response
            }

            def escapedServerId = escapeForJs(serverId)
            def escapedToken = escapeForJs(accessToken)
            def escapedUserId = escapeForJs(userId)
            def escapedAddress = escapeForJs(serverAddress)
            def now = System.currentTimeMillis() as long

            def script = """<script>
(function(){
  function injectCreds() {
    var creds = localStorage.getItem('jellyfin_credentials');
    try { creds = JSON.parse(creds); } catch(e) { creds = null; }
    if (!creds || !creds.Servers || creds.Servers.length === 0 || !creds.Servers[0].AccessToken || !creds.Servers[0].ManualAddress) {
      localStorage.setItem('jellyfin_credentials', JSON.stringify({
        \"Servers\": [{
          \"Id\": \"${escapedServerId}\",
          \"AccessToken\": \"${escapedToken}\",
          \"UserId\": \"${escapedUserId}\",
          \"DateLastAccessed\": ${now},
          \"LastConnectionMode\": 0,
          \"LocalAddress\": \"${escapedAddress}\",
          \"ManualAddress\": \"${escapedAddress}\",
          \"RemoteAddress\": \"${escapedAddress}\"
        }]
      }));
      window.location.replace('/web/index.html');
    }
  }
  injectCreds();
  (function() {
    function checkLogin(url) {
      if (url && url.toString().indexOf('/login') !== -1) {
        window.location.replace('/logout');
      }
    }
    var origPush = history.pushState.bind(history);
    var origReplace = history.replaceState.bind(history);
    history.pushState = function(s, t, url) { origPush(s, t, url); checkLogin(url); };
    history.replaceState = function(s, t, url) { origReplace(s, t, url); checkLogin(url); };
    window.addEventListener('popstate', function() { checkLogin(window.location.href); });
    window.addEventListener('hashchange', function() { checkLogin(window.location.hash); });
  })();
})();
</script>"""

            def modifiedBody = body.replaceFirst('(?i)</head>', java.util.regex.Matcher.quoteReplacement(script + '</head>'))
            if (modifiedBody == body) {
                return response
            }

            response.entity.setString(modifiedBody)
            response.headers.remove('Content-Length')
            response.headers.remove('Content-Encoding')
            return response
        } catch (Exception e) {
            logError('[JellyfinResponseRewriter] Failed to rewrite response', e)
            return response
        }
    })
} catch (Exception e) {
    logError('[JellyfinResponseRewriter] Failed before handling request', e)
    return next.handle(context, request)
}
