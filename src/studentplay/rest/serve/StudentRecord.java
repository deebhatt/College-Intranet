package studentplay.rest.serve;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/student")
public class StudentRecord {
		private static Map<Integer, Student> studentDB = new ConcurrentHashMap<Integer, Student>();
		private static AtomicInteger idCounter = new AtomicInteger();
	   
		@POST
		@Consumes(MediaType.APPLICATION_JSON)
		public Response createStudent(Student student)
		{
			student.setId(idCounter.incrementAndGet());
			studentDB.put(student.getId(), student);
			System.out.println("Created Student with Id:"+ student.getId());
			return Response.created(URI.create("/" + student.getId())).build();
		}
		
		@GET
		@Produces(MediaType.APPLICATION_JSON)
		@Path("{id}")
		public Student getStudent(@PathParam("id") int id)
		{
			final Student student = studentDB.get(id);
		      if (student == null) {
		         throw new WebApplicationException(Response.Status.NOT_FOUND);
		      }
		      return student;
		}
		
		@GET
		@Path("/display")
		public String display()
		{
			return "Student Display";
		}
}
