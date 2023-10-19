package fr.marstech.mtsftp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MtSftpApplication

fun main(args: Array<String>) {
	runApplication<MtSftpApplication>(*args)
}
