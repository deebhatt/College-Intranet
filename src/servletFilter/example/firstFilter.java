package servletFilter.example;

import java.io.IOException;
import java.util.Date;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

public class firstFilter implements Filter{

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void doFilter(HttpServletRequest arg0, HttpServletResponse arg1, FilterChain arg2) throws IOException,
			ServletException {
		String clientIp = arg0.getRemoteAddr();
		
		System.out.println("Ip ="+clientIp+"and Time ="+new Date().toString());
		
		arg2.doFilter(arg0, arg1);
		
	}

	@Override
	public void init(FilterConfig arg0) throws ServletException {
		String initializatonParam = arg0.getInitParameter("param1");
		
		System.out.println("First Init Param ="+initializatonParam);
		
	}

}
