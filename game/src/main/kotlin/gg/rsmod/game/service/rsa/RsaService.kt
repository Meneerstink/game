package gg.rsmod.game.service.rsa

import gg.rsmod.game.Server
import gg.rsmod.game.model.World
import gg.rsmod.game.service.Service
import gg.rsmod.util.ServerProperties
import mu.KLogging
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemReader
import org.bouncycastle.util.io.pem.PemWriter
import java.io.IOException
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Security
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

/**
 * @author Tom <rspsmods@gmail.com>
 */
class RsaService : Service {
    private lateinit var keyPath: Path

    private lateinit var exponent: BigInteger

    private lateinit var modulus: BigInteger

    private var radix = -1

    override fun init(
        server: Server,
        world: World,
        serviceProperties: ServerProperties,
    ) {
        keyPath = Paths.get(serviceProperties.getOrDefault("path", "./data/rsa/key.pem"))
        radix = serviceProperties.getOrDefault("radix", 16)

        if (!Files.exists(keyPath)) {
            /*
             * Audit S-05: the private key is no longer kept in git (.pem files under data/rsa are ignored), so a fresh checkout or
             * deploy has none. This used to ask a yes/no question on stdin and then block on scanner.next() - a server
             * started without a console hung at boot. Generate a new pair without asking and log the public modulus,
             * which the client needs (ClientConfig: the RSA modulus next to BigInteger("10001")); until the client
             * carries it, logins fail with a bad session id.
             */
            logger.warn("Private RSA key was not found in path: {} - generating a new key pair.", keyPath.toAbsolutePath())
            keyPath.toAbsolutePath().parent?.let { Files.createDirectories(it) }
            createPair(bitCount = serviceProperties.getOrDefault("bit-count", 2048))
            if (!Files.exists(keyPath)) {
                throw IOException("Could not write the new RSA private key to ${keyPath.toAbsolutePath()}.")
            }
        }

        try {
            PemReader(Files.newBufferedReader(keyPath)).use { reader ->
                val pem = reader.readPemObject()
                val keySpec = PKCS8EncodedKeySpec(pem.content)

                Security.addProvider(BouncyCastleProvider())
                val factory = KeyFactory.getInstance("RSA", "BC")

                val privateKey = factory.generatePrivate(keySpec) as RSAPrivateKey
                exponent = privateKey.privateExponent
                modulus = privateKey.modulus
            }
        } catch (exception: Exception) {
            throw ExceptionInInitializerError(
                IOException("Error parsing RSA key pair: ${keyPath.toAbsolutePath()}", exception),
            )
        }
    }

    override fun postLoad(
        server: Server,
        world: World,
    ) {
    }

    override fun bindNet(
        server: Server,
        world: World,
    ) {
    }

    override fun terminate(
        server: Server,
        world: World,
    ) {
    }

    /**
     * Credits: Apollo
     *
     * @author Graham
     * @author Major
     * @author Cube
     */
    private fun createPair(bitCount: Int) {
        Security.addProvider(BouncyCastleProvider())

        val keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC")
        keyPairGenerator.initialize(bitCount)
        val keyPair = keyPairGenerator.generateKeyPair()

        val privateKey = keyPair.private as RSAPrivateKey
        val publicKey = keyPair.public as RSAPublicKey

        val exponentText = publicKey.publicExponent.toString(radix)
        val modulusText = publicKey.modulus.toString(radix)
        println("")
        println("Place these keys in the client (find BigInteger(\"10001\" in client code):")
        println("--------------------")
        println("public key: $exponentText")
        println("modulus: $modulusText")
        println("")
        logger.warn(
            "NEW RSA KEY PAIR - put this public modulus (radix {}) in the client, next to exponent {}: {}",
            radix,
            exponentText,
            modulusText,
        )

        try {
            PemWriter(Files.newBufferedWriter(keyPath)).use { writer ->
                writer.writeObject(PemObject("RSA PRIVATE KEY", privateKey.encoded))
            }
            // Audit S-05: the public half next to the key, so the owner can copy it into the client. Not a secret.
            val publicFile = keyPath.resolveSibling("modulus.txt")
            Files.write(
                publicFile,
                listOf("radix=$radix", "exponent=$exponentText", "modulus=$modulusText"),
            )
            logger.warn("The client modulus was also written to {}.", publicFile.toAbsolutePath())
        } catch (e: Exception) {
            logger.error(e) { "Failed to write private key to ${keyPath.toAbsolutePath()}" }
        }
    }

    fun getExponent(): BigInteger = exponent

    fun getModulus(): BigInteger = modulus

    companion object : KLogging() {
        @JvmStatic
        fun main(args: Array<String>) {
            val radix = args[0].toInt()
            val bitCount = args[1].toInt()
            val path = args[2]

            val service = RsaService()
            service.keyPath = Paths.get(path)
            service.radix = radix

            val directory = service.keyPath.parent.toAbsolutePath()
            if (!Files.exists(directory)) {
                Files.createDirectory(directory)
            }

            logger.info("Generating RSA key pair...")
            service.createPair(bitCount)
        }
    }
}
