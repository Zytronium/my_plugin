package org.zytronium.myPlugin

import com.google.common.primitives.Doubles.max
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.util.Vector
import kotlin.random.Random
import org.bukkit.plugin.java.JavaPlugin
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.bukkit.entity.LargeFireball
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityExplodeEvent
import java.util.UUID

class MyPlugin : JavaPlugin(), Listener {

    override fun onEnable() {
        // Plugin startup logic
        server.pluginManager.registerEvents(this, this)
        this.getCommand("nuke")?.setExecutor(NukeCommandExecutor(server))
        logger.info("Test plugin enabled!")
    }


    override fun onDisable() {
        // Plugin shutdown logic
        logger.info("Test plugin disabled.")
    }

    // CommandExecutor for the /nuke command
    class NukeCommandExecutor(private val server: Server) : CommandExecutor {
        override fun onCommand(
            sender: CommandSender,
            command: Command,
            label: String,
            args: Array<out String>?
        ): Boolean {
            if(args.isNullOrEmpty()) {
                sender.sendMessage("Usage: /nuke <player|entityUUID> [missile_mode]")
                return false
            }


            val targetNameOrUUID = args[0]
            val missileMode = args.getOrNull(1)?.toBoolean() ?: false

            val targetEntity = getEntityByNameOrUUID(targetNameOrUUID)
            if(targetEntity == null) {
                sender.sendMessage("No player or entity found with the name/UUID '$targetNameOrUUID'.")
                return false
            }


            // Begin the nuke logic
            sender.sendMessage("Nuking ${targetEntity.name}...")
            server.dispatchCommand(
                Bukkit.getConsoleSender(),
                "tellraw @a \"NUCLEAR STRIKE INCOMING\""
            )


            nuke(targetEntity, missileMode)
            return true
        }

        fun send_cmd(cmd: String, vanillaCmd: Boolean = true): String {
            // Create a ByteArrayOutputStream to capture the command output
            val outputStream = ByteArrayOutputStream()
            val printStream = PrintStream(outputStream)

            // Save the current system output
            val originalSystemOut = System.out
            System.setOut(printStream)

            // Dispatch the command to the console sender
            Bukkit.getServer().dispatchCommand(
                Bukkit.getConsoleSender(),
                "minecraft:${cmd.removePrefix("/")}"
            )

            // Restore the original system output
            System.setOut(originalSystemOut)

            // Return the captured output as a string
            return outputStream.toString().trim()
        }

        fun fancyText(
            text: String = "",
            color: String = "white",
            bold: Boolean = false,
            italic: Boolean = false,
            underlined: Boolean = false,
            strikethrough: Boolean = false,
            obfuscated: Boolean = false
        ): String {
            return "{\"text\":\"$text\",\"color\":\"$color\",\"bold\":$bold,\"italic\":$italic,\"underlined\":$underlined,\"strikethrough\":$strikethrough,\"obfuscated\":$obfuscated}"
        }

        fun printmc(
            msg: String = "",
            color: String = "white",
            bold: Boolean = false,
            italic: Boolean = false,
            underlined: Boolean = false,
            strikethrough: Boolean = false,
            obfuscated: Boolean = false
        ) {
            if(color != "white" || bold || italic || underlined || strikethrough || obfuscated) {
                send_cmd(
                    "/tellraw @a ${
                        fancyText(
                            msg,
                            color,
                            bold,
                            italic,
                            underlined,
                            strikethrough,
                            obfuscated
                        )
                    }"
                )
            } else {
                send_cmd("/tellraw @a \"$msg\"")
            }
        }

        fun getEntityByNameOrUUID(identifier: String): Entity? {
            return try {
                // Try to interpret identifier as a UUID
                val uuid = UUID.fromString(identifier)
                Bukkit.getEntity(uuid) // Get entity by UUID
            } catch (e: IllegalArgumentException) {
                // If identifier is not a valid UUID, try getting player by name
                Bukkit.getPlayer(identifier)
            }
        }

        private fun nuke(entity: Entity, missileMode: Boolean = false) {
            repeat(8) {
                var targetLocation = entity.location.add(
                    Random.nextDouble(-19.0, 19.0),
                    if(missileMode) 375.0 + Random.nextDouble(-4.0, 4.0) else Random.nextDouble(-3.0, 3.0),
                    Random.nextDouble(-19.0, 19.0)
                )

                targetLocation.y = max(targetLocation.y, 350.0)
                spawnFireballAt(targetLocation)
            }
        }

        private fun spawnFireballAt(location: Location) {
            val fireball =
                location.world?.spawn(location, LargeFireball::class.java)
            fireball?.let {
                it.customName = "nuke"
                it.velocity = Vector(0.0, -10.0, 0.0)
                it.direction = Vector(0.0, -1.0, 0.0)
                it.yield = 191F // 150% vanilla maximum yield
            }
        }
    }

    @EventHandler
    fun onNukeExplosion(event: EntityExplodeEvent) {
        val entity = event.entity

        // Check if the exploding entity is a "nuke" fireball
        if(entity is LargeFireball && entity.customName == "nuke") {
            val explosionLocation = entity.location
            val thunderRadius = 5250.0
            val impactSoundRadius = 250.0
            val maxVolume = 10.0 // Max volume for closest players
            val soundDistance = 18

            entity.world.players.forEach { player ->
                val distanceToExplosion =
                    player.location.distance(explosionLocation)

                if(distanceToExplosion <= impactSoundRadius) {
                    // Calculate volume based on distance (closer = louder, further = softer)
                    val impactVolume =
                        (maxVolume * (impactSoundRadius - distanceToExplosion) / impactSoundRadius).coerceAtLeast(
                            0.05
                        )

                    // Determine sound location
                    val soundLocation =
                        if(distanceToExplosion < soundDistance) {
                            explosionLocation // Play at explosion location if closer than soundDistance
                        } else {
                            // Calculate a position soundDistance blocks closer to the explosion
                            val directionToExplosion = explosionLocation.clone()
                                .subtract(player.location).toVector()
                                .normalize()
                            player.location.add(
                                directionToExplosion.multiply(
                                    soundDistance
                                )
                            )
                        }

                    // Play impact sound for the player at the calculated location with dynamic volume
                    player.playSound(
                        soundLocation,
                        Sound.ENTITY_LIGHTNING_BOLT_IMPACT,
                        impactVolume.toFloat(),
                        0.4F
                    )
                }

                if(distanceToExplosion <= thunderRadius) {
                    // Calculate volume for thunder sound based on distance within thunder radius
                    val thunderVolume =
                        (maxVolume * (thunderRadius - distanceToExplosion) / thunderRadius).coerceAtLeast(
                            0.15
                        )

                    // Determine thunder sound location
                    val thunderLocation =
                        if(distanceToExplosion < soundDistance) {
                            explosionLocation // Play at explosion location if closer than soundDistance
                        } else {
                            // Calculate a position soundDistance blocks closer for thunder sound
                            val directionToExplosion = explosionLocation.clone()
                                .subtract(player.location).toVector()
                                .normalize()
                            player.location.add(
                                directionToExplosion.multiply(
                                    soundDistance
                                )
                            )
                        }

                    // Play thunder sound at the calculated location with dynamic volume
                    player.playSound(
                        thunderLocation,
                        Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                        thunderVolume.toFloat(),
                        0.35F
                    )
                }
            }
        }
    }

}
