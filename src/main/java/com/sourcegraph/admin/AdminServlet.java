package com.sourcegraph.admin;

import com.atlassian.templaterenderer.TemplateRenderer;


import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import java.net.URI;
import com.atlassian.sal.api.auth.LoginUriProvider;
import com.atlassian.sal.api.user.UserManager;



@Component("adminServlet")
public class AdminServlet extends HttpServlet
{

    private final TemplateRenderer templateRenderer;
    private final UserManager userManager;
    private final LoginUriProvider loginUriProvider;

  @Autowired
  public AdminServlet(@ComponentImport TemplateRenderer templateRenderer, @ComponentImport UserManager userManager, @ComponentImport LoginUriProvider loginUriProvider)
  {
      this.templateRenderer = templateRenderer;
      this.userManager = userManager;
      this.loginUriProvider = loginUriProvider;
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
        String username = userManager.getRemoteUsername(request);
        if (username == null || !userManager.isSystemAdmin(username))
        {
          redirectToLogin(request, response);
          return;
        }

        response.setContentType("text/html;charset=utf-8");
        templateRenderer.render("admin.vm", response.getWriter());
  }

  private void redirectToLogin(HttpServletRequest request, HttpServletResponse response) throws IOException
  {
        response.sendRedirect(loginUriProvider.getLoginUri(getUri(request)).toASCIIString());
  }

  private URI getUri(HttpServletRequest request)
    {
        StringBuffer builder = request.getRequestURL();
        if (request.getQueryString() != null)
        {
            builder.append("?");
            builder.append(request.getQueryString());
        }
        return URI.create(builder.toString());
    }

}
