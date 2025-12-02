// Routes.kt (sketch) — Week 7 Inline Edit
package comp2850

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

data class Task(val id: Int, val title: String)

fun ApplicationCall.isHtmx() = request.headers["HX-Request"] == "true"

// Replace with your actual renderer
fun render(tpl: String, model: Map<String, Any?>): String = "<!-- render $tpl with $model -->"

fun Route.inlineEditRoutes(repo: TaskRepo) {
    get("/tasks/{id}/edit") {
        val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.NotFound)
        val task = TaskRepository.find(id) ?: return@get call.respond(HttpStatusCode.NotFound)
        val errorParam = call.request.queryParameters["error"]

        val errorMessage = when (errorParam) {
            "blank" -> "Title is required. Please enter at least one character."
            else -> null
        }

        if (call.isHtmx()) {
            val template = pebble.getTemplate("templates/tasks/_edit.peb")
            val model = mapOf("task" to task, "error" to errorMessage)
            val writer = StringWriter()
            template.evaluate(writer, model)
            call.respondText(writer.toString(), ContentType.Text.Html)
        } else {
            val model = mapOf(
                "title" to "Tasks",
                "tasks" to TaskRepository.all(),
                "editingId" to id,
                "errorMessage" to errorMessage
            )
            val template = pebble.getTemplate("templates/tasks/index.peb")
            val writer = StringWriter()
            template.evaluate(writer, model)
            call.respondText(writer.toString(), ContentType.Text.Html)
        }
    }
    
    post("/tasks/{id}/edit") {
        val id = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.NotFound)
        val task = TaskRepository.find(id) ?: return@post call.respond(HttpStatusCode.NotFound)

        val newTitle = call.receiveParameters()["title"].orEmpty().trim()

        // Validation
        if (newTitle.isBlank()) {
            if (call.isHtmx()) {
                // HTMX path: return edit fragment with error
                val template = pebble.getTemplate("templates/tasks/_edit.peb")
                val model = mapOf(
                    "task" to task,
                    "error" to "Title is required. Please enter at least one character."
                )
                val writer = StringWriter()
                template.evaluate(writer, model)
                return@post call.respondText(writer.toString(), ContentType.Text.Html, HttpStatusCode.BadRequest)
            } else {
                // No-JS path: redirect with error flag
                return@post call.respondRedirect("/tasks/${id}/edit?error=blank")
            }
        }

        // Update task
        task.title = newTitle
        TaskRepository.update(task)

        if (call.isHtmx()) {
            // HTMX path: return view fragment + OOB status
            val viewTemplate = pebble.getTemplate("templates/tasks/_item.peb")
            val viewWriter = StringWriter()
            viewTemplate.evaluate(viewWriter, mapOf("task" to task))

            val status = """<div id="status" hx-swap-oob="true">Task "${task.title}" updated successfully.</div>"""

            return@post call.respondText(viewWriter.toString() + status, ContentType.Text.Html)
    }

    get("/tasks/{id}/view") {
        val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.NotFound)
        val task = TaskRepository.find(id) ?: return@get call.respond(HttpStatusCode.NotFound)

        // HTMX path only (cancel is just a link to /tasks in no-JS)
        val template = pebble.getTemplate("templates/tasks/_item.peb")
        val model = mapOf("task" to task)
        val writer = StringWriter()
        template.evaluate(writer, model)
        call.respondText(writer.toString(), ContentType.Text.Html)
    }

    // No-JS path: PRG redirect
    call.respondRedirect("/tasks")
    }
}

interface TaskRepo { fun get(id:Int): Task; fun update(id:Int, title:String): Task }

