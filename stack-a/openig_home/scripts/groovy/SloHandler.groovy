import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise
import java.net.URLEncoder
import java.net.URI

def CANONICAL_ORIGIN = System.getenv('CANONICAL_ORIGIN_APP1') ?: 'http://wp-a.sso.local'

def publicUrl = System.getenv('OPENIG_PUBLIC_URL') ?: 'http://openiga.sso.local:80'
def hostHeader = request?.headers?.getFirst('Host')?.trim()
def publicHost = new URI(publicUrl).host ?: 'openiga.sso.local'
def host = hostHeader ?: publicHost
def hostWithPort = host.contains(':') ? host : (host + ':80')
def hostWithoutPort = hostWithPort.endsWith(':80') ? hostWithPort[0..-4] : hostWithPort

def idToken = session['oauth2:http://' + hostWithPort + '/openid/app1']?.get('atr')?.get('id_token')
if (!idToken) {
    idToken = session['oauth2:http://' + hostWithoutPort + '/openid/app1']?.get('atr')?.get('id_token')
}
if (!idToken) {
    idToken = session['oauth2:' + publicUrl + '/openid/app1']?.get('atr')?.get('id_token')
}

session.clear()

def baseLogoutUrl = 'http://auth.sso.local:8080/realms/sso-realm/protocol/openid-connect/logout'
def postLogoutRedirectUri = CANONICAL_ORIGIN + '/'
def params = 'client_id=openig-client&post_logout_redirect_uri=' + URLEncoder.encode(postLogoutRedirectUri, 'UTF-8')
if (idToken) {
    params += '&id_token_hint=' + URLEncoder.encode(idToken as String, 'UTF-8')
}
def logoutUrl = baseLogoutUrl + '?' + params
logger.warn('SloHandler: logout URL = ' + logoutUrl)

def response = new Response(Status.FOUND)
response.headers['Location'] = logoutUrl
return newResultPromise(response)
