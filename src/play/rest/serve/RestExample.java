package play.rest.serve;

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
import javax.ws.rs.core.Response;

@Path("/calc")
public class RestExample {

	@GET
	@Path("/sum/{a}/{b}")
	public Response sayHello(@PathParam("a") int a, @PathParam("b") int b)
	{
		int result = a+b;
		return Response.status(200).entity("sum :"+ result).build();
	}
	
	@GET
	@Path("/display")
	public String display()
	{
		return "Calc Display";
	}
	

	   private static Map<Integer, Customer> customerDB = new ConcurrentHashMap<Integer, Customer>();
	   private static AtomicInteger idCounter = new AtomicInteger();
	   
	   @Path("/customer")
	   @POST
	   @Consumes("application/xml")
	   public Response createCustomer(Customer customer) {
	      //Customer customer = readCustomer(is);
	      customer.setId(idCounter.incrementAndGet());
	      customerDB.put(customer.getId(), customer);
	      System.out.println("Created customer " + customer.getId());
	      return Response.created(URI.create("/" + customer.getId())).build();

	   }
	   
	   @GET
	   @Path("/customer/{id}")
	   @Produces("application/xml")
	   public Customer getCustomer(@PathParam("id") int id) {
	      final Customer customer = customerDB.get(id);
	      if (customer == null) {
	         throw new WebApplicationException(Response.Status.NOT_FOUND);
	      }
	      return customer;
	   }
}
