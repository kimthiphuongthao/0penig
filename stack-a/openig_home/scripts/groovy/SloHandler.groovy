import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise
import java.net.URLEncoder

def publicUrl = System.getenv('OPENIG_PUBLIC_URL') ?: 'http://openiga.sso.local:80'
def idToken = session['oauth2:' + publicUrl + '/openid/app1']?.get('atr')?.get('id_token')

session.clear()

def baseLogoutUrl = 'http://auth.sso.local:8080/realms/sso-realm/protocol/openid-connect/logout'
def params = 'client_id=openig-client&post_logout_redirect_uri=' + URLEncoder.encode('http://openiga.sso.local/app1/', 'UTF-8')
if (idToken) {
    params += '&id_token_hint=' + URLEncoder.encode(idToken as String, 'UTF-8')
}
def logoutUrl = baseLogoutUrl + '?' + params
logger.warn('SloHandler: logout URL = ' + logoutUrl)

def response = new Response(Status.FOUND)
response.headers['Location'] = logoutUrl
return newResultPromise(response)
