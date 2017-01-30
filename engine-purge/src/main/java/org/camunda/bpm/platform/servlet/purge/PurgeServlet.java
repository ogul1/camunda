package org.camunda.bpm.platform.servlet.purge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.engine.impl.ManagementServiceImpl;
import org.camunda.bpm.engine.impl.management.PurgeReport;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 *
 * Servlet that actually purges the database on GET request to root endpoint
 * 
 * @author Askar Akhmerov
 */
@WebServlet(name="PurgeServlet", urlPatterns={"/*"})
public class PurgeServlet extends HttpServlet {
  private ManagementServiceImpl managementService;
  private ObjectMapper objectMapper;

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    managementService = (ManagementServiceImpl) config.getServletContext().getAttribute("managementService");
    this.objectMapper = new ObjectMapper();
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    PurgeReport purgeReport = managementService.purge();
    resp.setStatus(200);
    resp.setContentType("application/json");
    resp.setCharacterEncoding("UTF-8");
    resp.getWriter().println(objectMapper.writeValueAsString(purgeReport));
    resp.getWriter().flush();
  }
}
