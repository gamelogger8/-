AppAuth-Android SDK开发指南

目录<br/>
1	简介<br/>	
2	获取<br/>	
3	适用范围以及约束<br/>
4	使用场景<br/>
5	集成	<br/>
5.1	添加编译依赖	<br/>
5.2	登录修改AndroidManifest.xml	<br/>
5.3	准备实例	<br/>
5.3.1	创建AuthState实例	<br/>
5.3.2	创建AuthorizationServiceConfiguration实例	<br/>
5.4	登录	<br/>
5.4.1	创建AuthorizationRequest实例	<br/>
5.4.2	获取登录的Intent	<br/>
5.4.3	执行startActivityForResult	<br/>
5.4.4	从onActivityResult获取登录认证信息	<br/>
5.5	获取各种Token	<br/>
5.6	获取用户信息	<br/>
5.7	登出/取消登录授权	<br/>
6	参考代码	<br/>

1	简介
AppAuth for Android是一个Android设备的客户端SDK，用于与OAuth 2.0和OpenID Connect服务提供方进行通信。它严格按照规范的请求和响应实现，同时遵循实现语言的惯用风格。除了映射原始协议流之外，还提供了便捷方法来协助执行常见任务，例如使用新令牌执行操作。<br/>
该SDK遵循OAuth 2.0 for Native Apps(RFC8252)中设置的最佳实践，包括使用Custom Tabs进行身份验证请求，因此，由于可用性和安全性原因，明确不支持WebView。<br/>
该SDK还支持对OAuth的PKCE扩展，该扩展是为了在使用自定义URI方案重定向时保护公共客户端中的授权代码而创建的。该SDK对其他扩展（标准或其他）很友好，能够处理所有协议请求和响应中的其他参数。<br/>
由于Google帐号也支持OAuth2.0和OpenID Connect规范，因此该SDK可以与Google的帐号服务通讯，并完成登录授权，整个过程不依赖Google Play Service。<br/>
2	获取<br/>
AppAuth-Android SDK是由OpenID规范组织维护的开源项目，包含SDK Library工程和一个Demo工程。<br/>
项目地址：https://github.com/openid/AppAuth-Android<br/>
接口文档地址：http://openid.github.io/AppAuth-Android/docs/latest/<br/>
3	适用范围以及约束<br/>
Custom URI Schemes：Android所有支持版本<br/>
APP Link：安卓M / API 23+<br/>
限制：目前只支持授权码的模式，不支持IdToken模式。<br/>
4	使用场景<br/>
AppAuth SDK主要针对设备中没有GMS服务，但是需要使用Google账号进行登陆授权的场景，App可以集成AppAuth SDK完成Google账号的登陆授权流程，登陆授权后可以访问Google或三方提供的资源；<br/>

 ![1](https://user-images.githubusercontent.com/102587314/163743988-999e9fde-c8d7-4192-9895-327b03267de4.png)

主要流程描述如下；
1)	App注册一个回调接口(浏览器在完成授权后会调用这个接口)，调用AppAuth的的授权请求接口；
2)	拉起浏览器并跳转到Google的登陆授权页面，输入Google账号和密码，点击登陆，登陆成功后，跳转到授权页面，点击允许授权；
3)	Google Oauth服务器返回授权码，浏览器拉起App(通过注册的回调接口)；
4)	浏览器通过intent把授权码返回给APP；
5)	App通过授权码发送请求Access Token到Google Token服务器；
6)	Google Token服务器验证授权码，并返回Access Token给App；
7)	App通过Access Token请求资源服务器上的资源；
8)	资源服务器验证Access Token，并返回请求的资源；<br/>

5	集成<br/>
下面通过一个实例描述如何集成AppAuth-SDK。<br/>

5.1	添加编译依赖<br/>
将以下行添加到您的app的build.gradle文件中：<br/>
```
dependencies {
    // AppAuth SDK
    implementation 'net.openid:appauth：LatestVersion'>
    // For decode ID Token<br/>
    implementation 'com.auth0.android:jwtdecode:2.0.0'
    // Volley
    implementation 'com.android.volley:volley:1.1.1'
}
```
后续HTTP网络请求需要使用Volley（如果不集成登出/取消登录授权，则不需要使用Volley），提出Id Token里面的用户信息需要使用JWT，因此这里也添加依赖项。<br/>

5.2	登录修改AndroidManifest.xml<br/>
在AndroidManifest.xml里添加以下代码。其中，{your app package}是你的APP的包名。<br/>
```
<activity
    android:name="net.openid.appauth.RedirectUriReceiverActivity"
    tools:node="replace">
    <intent-filter>          
        <action android:name="android.intent.action.VIEW"/> 
        <category android:name="android.intent.category.DEFAULT"/>
        <category android:name="android.intent.category.BROWSABLE"/> 
        <data android:scheme="com.sample.hmssample.appauthdemo"/> 
    </intent-filter> 
    <intent-filter> 
        <action android:name="android.intent.action.VIEW"/>
        <category android:name="android.intent.category.DEFAULT"/> 
        <category android:name="android.intent.category.BROWSABLE"/> 
        <data android:scheme="{your app package}"/> 
    </intent-filter>  
</activity> 
```
5.3	准备实例<br/>
5.3.1	创建AuthState实例<br/>
创建AuthState实例，并代入”{}”，代表没有登录信息。<br/>
private val appAuthState: AuthState = AuthState.jsonDeserialize("{}")<br/>

5.3.2	创建AuthorizationServiceConfiguration实例<br/>
创建AuthorizationServiceConfiguration实例，并在创建时导入Google OAuth服务器的授权端点和令牌端点。<br/>
```
private val config = AuthorizationServiceConfiguration(
    Uri.parse(AUTHORIZATION_ENDPOINT_URI),
    Uri.parse(TOKEN_ENDPOINT_URI)
)
```
Google OAuth服务器的授权端点和令牌端点分别是：<br/>
private const val AUTHORIZATION_ENDPOINT_URI = "https://accounts.google.com/o/oauth2/v2/auth"<br/>
private const val TOKEN_ENDPOINT_URI = "https://www.googleapis.com/oauth2/v4/token"<br/>

5.4	登录<br/>
5.4.1	创建AuthorizationRequest实例<br/>
拥有AuthorizationServiceConfiguration实例后，创建一个AuthorizationRequest实例。<br/>
```
val authorizationRequest = AuthorizationRequest
    .Builder(
        config,
        cliendId,
        ResponseTypeValues.CODE,
        Uri.parse(redirectUri)
    )
    .setScope(scope)
    .build()
```
	config是创建了的AuthorizationServiceConfiguration实例<br/>
	clientId是在Google play Console上的App申请的OAuth客户端认证的客户端id。<br/>
	redirectUri是 {你的APP的包名}:/{path} 。例如：com.example.app:/ oauth2redirect<br/>
	scope是请求授权的范围。例如："openid email profile"<br/>

5.4.2	获取登录的Intent<br/>
拥有AuthorizationRequest实例后，通过AuthorizationService获取登录用的Intent。<br/>
```
val intent = AuthorizationService(context).getAuthorizationRequestIntent(authorizationRequest)
```
5.4.3	执行startActivityForResult<br/>
使用startActivityForResult执行intent。登录结果会在onActivityResult返回。<br/>
```
startActivityForResult(intent, requestCode)
```
![2](https://user-images.githubusercontent.com/102587314/163744557-955be492-644c-4220-8da2-7fed889fa4c6.jpg)

5.4.4	从onActivityResult获取登录认证信息<br/>
使用AuthorizationResponse，从data获取登录认证信息。<br/>
使用AuthorizationException，从data获取错误信息。<br/>
```
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    when (requestCode) {
        this.requestCode -> {
            data?.let { it ->
                val response = AuthorizationResponse.fromIntent(it)
                val exception = AuthorizationException.fromIntent(it)
                appAuthState.update(response, exception)
            }
        }
    }
}
```
如果能成功获取response，并且exception是空，就代表成功获取了登录认证信息。<br/>
成功获取了登录认证信息后，通过执行AuthState的update，把它保存到appAuthState（AuthState的实例）里面。<br/>

5.5	获取各种Token<br/>
通过执行AuthorizationService的performTokenRequest，获取Access Token，Refresh Token和Id Token。执行performTokenRequest时，需要使用从onActivityResult获取的AuthorizationResponse（登录认证信息）。<br/>
返回结果后，再次执行AuthState的update，把结果保存到appAuthState（AuthState的实例）里面。之后就能够从appAuthState里面获取Access Token，Refresh Token和Id Token。<br/>
```
AuthorizationService(context)
    .performTokenRequest(response.createTokenExchangeRequest()) { responseWork, authorizationExceptionWork ->
        appAuthState.update(responseWork, authorizationExceptionWork)
        responseWork?.let {
            val accessToken = appAuthState.accessToken
            val refreshToken = appAuthState.refreshToken
            val idToken = appAuthState.idToken
        }
    }
```
5.6	获取用户信息<br/>
使用JWT提取Id Token里面的用户信息。<br/>
```
val jwt = JWT(idToken)
openId = jwt.subject
name = jwt.claims["name"]?.asString()
familyName = jwt.claims["family_name"]?.asString()
givenName = jwt.claims["given_name"]?.asString()
pictureUrl = jwt.claims["picture"]?.asString()
email = jwt.claims["email"]?.asString()
emailVerified = jwt.claims["email_verified"]?.asBoolean()
```
```
从jwt能获得以下用户信息：
  Open Id：jwt的subject
	姓名：jwt的name
	姓：jwt的family_name
	名：jwt的given_name
	图像URL：jwt的picture
	邮箱：jwt的email
	否有效的邮箱：jwt的email_verified
```
![4 jpg](https://user-images.githubusercontent.com/102587314/163745760-8629216e-37f4-41e4-88f0-a52c354cde8f.png)

5.7	登出/取消登录授权<br/>
AppAuth没有提供登出/取消登录授权的功能，如要集成登出/取消登录授权的功能，需要直接调用Google提供的取消Token的API。<br/>
Google提供的取消Token授权的API是：https://accounts.google.com/o/oauth2/revoke <br/>
使用这个API取消Access Token的授权后，Refresh Token的授权也会同时被取消。<br/>
下面的代码是如何调用这个API的例：<br/>
```
abstract class ResponseCallback<T> {
    abstract fun onResponse(response: T?)
    abstract fun onError(error: VolleyError?)
}
```
```
fun postFormUrlencodedRequest(
    context: Context,
    url: String,
    headers: MutableMap<String, String>?,
    params: MutableMap<String, String>?,
    callback: ResponseCallback<String>?
) {
    val queue = Volley.newRequestQueue(context)

    val request: StringRequest = object : StringRequest(
        Method.POST,
        url,
        Response.Listener<String> { response -> callback?.onResponse(response) },
        Response.ErrorListener { error -> callback?.onError(error) }
    ) {
        override fun getBodyContentType(): String {
            return "application/x-www-form-urlencoded"
        }

        override fun getHeaders(): MutableMap<String, String> {
            return headers ?: super.getHeaders()
        }

        override fun getParams(): MutableMap<String, String> {
            return params ?: super.getParams()
        }
    }

    queue.add(request)
}
```
```
fun signOut(context: Context) {
    val accessToken = appAuthState.accessToken ?: return

    val params: MutableMap<String, String> = HashMap()
    params["token"] = accessToken

    val httpRequest = HttpRequest()
    httpRequest.postFormUrlencodedRequest(
        context,
        "https://accounts.google.com/o/oauth2/revoke",
        null,
        params,
        object : HttpRequest.ResponseCallback<String>() {
            override fun onResponse(response: String?) {
                // 成功
            }
            override fun onError(error: VolleyError?) {
                // 失败
            }
        }
    )
}
```

6	参考代码 <br/>

