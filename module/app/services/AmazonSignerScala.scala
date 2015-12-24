package services

import java.security.MessageDigest
import java.text.{DateFormat, SimpleDateFormat}
import java.util.{Calendar, TimeZone}
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import org.apache.commons.codec.binary.Hex

import scala.collection.JavaConverters._

/**
  * http://docs.aws.amazon.com/general/latest/gr/sigv4-create-canonical-request.html <br />
  * http://docs.aws.amazon.com/general/latest/gr/sigv4-create-string-to-sign.html <br />
  * http://docs.aws.amazon.com/general/latest/gr/sigv4-calculate-signature.html <br />
  * http://docs.aws.amazon.com/general/latest/gr/sigv4-add-signature-to-request.html <br />
  * <p>
  * CanonicalRequest = <br />
  * HTTPRequestMethod + '\n' + <br />
  * CanonicalURI + '\n' + <br />
  * CanonicalQueryString + '\n' + <br />
  * CanonicalHeaders + '\n' + <br />
  * SignedHeaders + '\n' + <br />
  * HexEncode(Hash(RequestPayload))
  * <p>
  * StringToSign = <br />
  * Algorithm + '\n' + <br />
  * RequestDate + '\n' + <br />
  * CredentialScope + '\n' + <br />
  * HashedCanonicalRequest))
  * <p>
  * Authorization: AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20110909/us-east-1/iam/aws4_request, SignedHeaders=content-type;host;x-amz-date,
  * Signature=ced6826de92d2bdeed8f846f0bf508e8559e98e4b0199114b84c54174deb456c
  */
object AmazonSignerScala {
  val POST_METHOD: String = "POST"
  val GET_METHOD: String = "GET"

  private val BASE16MAP: Array[Char] = Array[Char]('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

  @throws(classOf[Exception])
  private def hash(data: String): Array[Byte] = {
    val md: MessageDigest = MessageDigest.getInstance("SHA-256")
    md.update(data.getBytes("UTF-8"))
    md.digest
  }


  private def toBase16(data: Array[Byte]): String = {
    val hexBuffer = new StringBuffer(data.length * 2)
    for (i <- data.indices) {
      hexBuffer.append(BASE16MAP((data(i) >> (4)) & 0xF))
      hexBuffer.append(BASE16MAP((data(i) >> (0)) & 0xF))
    }
    hexBuffer.toString
  }

  @throws(classOf[Exception])
  private def hmacSHA256(data: String, key: Array[Byte]): Array[Byte] = {
    val algorithm: String = "HmacSHA256"
    val mac: Mac = Mac.getInstance(algorithm)
    mac.init(new SecretKeySpec(key, algorithm))
    mac.doFinal(data.getBytes("UTF8"))
  }
}

class AmazonSignerScala {
  private var timestamp: String = null
  private var date: String = null
  private var signedHeaders: String = null
  private var awsAccessKeyId: String = null
  private var awsSecretKey: String = null
  private var region: String = null
  private val service: String = "es"
  private var host: String = null

  def this(awsAccessKeyId: String, awsSecretKey: String, region: String, host: String) {
    this()
    this.awsAccessKeyId = awsAccessKeyId
    this.awsSecretKey = awsSecretKey
    this.region = region
    this.host = host
  }

  @throws(classOf[Exception])
  def getSignedHeaders(method: String, uri: String, queryString: String, payload: String) = {
    var headersInput = Map[String, String]()
    setCurrentTimedate
    headersInput += "host" -> host
    headersInput += "x-amz-date" -> getTimestamp
    val canonicalRequest: String = createCanonicalRequest(method, uri, queryString, headersInput.asJava, payload)
    val stringToSign: String = createStringToSign(canonicalRequest)
    val signature: String = sign(stringToSign)
    val authorizationHeader: String = "AWS4-HMAC-SHA256 Credential=" + awsAccessKeyId + "/" + getCredentialScope + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature
    headersInput += "Authorization" -> authorizationHeader
    headersInput
  }


  @throws(classOf[Exception])
  private def sign(stringToSign: String): String = {
    Hex.encodeHexString(AmazonSignerScala.hmacSHA256(stringToSign, getSignatureKey))
  }

  private def setCurrentTimedate {
    val cal: Calendar = Calendar.getInstance
    val dfmT: DateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'")
    dfmT.setTimeZone(TimeZone.getTimeZone("GMT"))
    timestamp = dfmT.format(cal.getTime)
    val dfmD: DateFormat = new SimpleDateFormat("yyyyMMdd")
    dfmD.setTimeZone(TimeZone.getTimeZone("GMT"))
    date = dfmD.format(cal.getTime)
  }

  @throws(classOf[Exception])
  private def createCanonicalRequest(method: String, uri: String, queryString: String, headersInput: java.util.Map[String, String], payload: String): String = {
    val headersSorted: java.util.SortedMap[String, String] = new java.util.TreeMap[String, String](headersInput)
    val headers: StringBuffer = new StringBuffer
    val signedHeaders: StringBuffer = new StringBuffer
    val iter: java.util.Iterator[java.util.Map.Entry[String, String]] = headersSorted.entrySet.iterator
    while (iter.hasNext) {
      val kvpair: java.util.Map.Entry[String, String] = iter.next
      headers.append(kvpair.getKey + ":" + kvpair.getValue + "\n")
      signedHeaders.append(kvpair.getKey)
      if (iter.hasNext) {
        signedHeaders.append(";")
      }
    }
    this.signedHeaders = signedHeaders.toString
    method + "\n" + uri + "\n" + queryString + "\n" + headers.toString + "\n" + signedHeaders.toString + "\n" + AmazonSignerScala.toBase16(AmazonSignerScala.hash(payload))
  }

  @throws(classOf[Exception])
  private def createStringToSign(data: String): String = {
    "AWS4-HMAC-SHA256\n" + getTimestamp + "\n" + getCredentialScope + "\n" + AmazonSignerScala.toBase16(AmazonSignerScala.hash(data))
  }

  private def getCredentialScope: String = {
    getDate + "/" + region + "/" + service + "/aws4_request"
  }

  @throws(classOf[Exception])
  private def getSignatureKey: Array[Byte] = {
    val kSecret: Array[Byte] = ("AWS4" + awsSecretKey).getBytes("UTF8")
    val kDate: Array[Byte] = AmazonSignerScala.hmacSHA256(getDate, kSecret)
    val kRegion: Array[Byte] = AmazonSignerScala.hmacSHA256(region, kDate)
    val kService: Array[Byte] = AmazonSignerScala.hmacSHA256(service, kRegion)
    val kSigning: Array[Byte] = AmazonSignerScala.hmacSHA256("aws4_request", kService)
    kSigning
  }

  private def getTimestamp: String = timestamp

  private def getDate: String = date

}