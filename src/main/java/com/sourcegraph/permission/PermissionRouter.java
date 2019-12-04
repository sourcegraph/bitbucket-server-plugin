package com.sourcegraph.permission;

import com.atlassian.bitbucket.permission.Permission;
import com.atlassian.bitbucket.permission.PermissionService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositorySearchRequest;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.bitbucket.user.*;
import com.atlassian.bitbucket.util.Page;
import com.atlassian.bitbucket.util.PageRequest;
import com.atlassian.bitbucket.util.PageRequestImpl;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.sourcegraph.rest.Status;
import org.roaringbitmap.RoaringBitmap;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Path("/permissions")
@Component
public class PermissionRouter {
    @ComponentImport
    private static RepositoryService repositoryService;
    @ComponentImport
    private static UserManager userManager;
    @ComponentImport
    private static UserService userService;
    @ComponentImport
    private static SecurityService securityService;

    public PermissionRouter(RepositoryService repositoryService, UserManager userManager, UserService userService, SecurityService securityService) {
        PermissionRouter.repositoryService = repositoryService;
        PermissionRouter.userManager = userManager;
        PermissionRouter.userService = userService;
        PermissionRouter.securityService = securityService;
    }

    @GET
    @Path("/users")
    public Response getUsersWithRepositoryPermission(@Context HttpServletRequest request, @QueryParam("repository") String repository, @QueryParam("permission") String permission) throws IOException {
        UserProfile profile = userManager.getRemoteUser(request);
        if (profile == null || !userManager.isSystemAdmin(profile.getUserKey())) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Repository repo = getRepository(repository);
        if (repo == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("No such repository: " + repository).build();
        }

        Permission perm = getRepositoryPermission(permission);
        if (perm == null) {
            return Response.status(Status.UNPROCESSABLE_ENTITY).build();
        }

        RoaringBitmap bitmap = new RoaringBitmap();

        UserSearchRequest.Builder builder = new UserSearchRequest.Builder();
        builder.repositoryPermission(repo, perm);
        UserSearchRequest search = builder.build();

        PageRequest pageRequest = new PageRequestImpl(0, 100);
        do {
            Page<ApplicationUser> page = userService.search(search, pageRequest);
            System.out.println(page.getSize());
            for (ApplicationUser user : page.getValues()) {
                System.out.println(user.getDisplayName());
                bitmap.add(user.getId());
            }
            pageRequest = page.getNextPageRequest();
        } while (pageRequest != null);

        byte[] backing;
        try {
            backing = serialize(bitmap);
        } catch (IOException ex) {
            return Response.serverError().build();
        }
        return Response.ok(backing).build();
    }

    @GET
    @Path("/repositories")
    public Response getAccessibleRepositories(@Context HttpServletRequest request, @QueryParam("user") String name, @QueryParam("permission") String permission) {
        UserProfile profile = userManager.getRemoteUser(request);
        if (profile == null || !userManager.isSystemAdmin(profile.getUserKey())) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        ApplicationUser user = userService.getUserByName(name);
        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("No such user: " + user).build();
        }

        Permission perm = getRepositoryPermission(permission);
        if (perm == null) {
            return Response.status(Status.UNPROCESSABLE_ENTITY).build();
        }

        EscalatedSecurityContext context = securityService.impersonating(user, "Repository Search");
        RoaringBitmap bitmap = context.call(() -> {
            RoaringBitmap temp = new RoaringBitmap();

            RepositorySearchRequest.Builder builder = new RepositorySearchRequest.Builder();
            builder.permission(perm);
            RepositorySearchRequest search = builder.build();

            PageRequest pageRequest = new PageRequestImpl(0, 100);
            do {
                Page<Repository> page = repositoryService.search(search, pageRequest);
                for (Repository repository : page.getValues()) {
                    temp.add(repository.getId());
                }
                pageRequest = page.getNextPageRequest();
            } while (pageRequest != null);
            return temp;
        });

        byte[] backing;
        try {
            backing = serialize(bitmap);
        } catch (IOException ex) {
            return Response.serverError().build();
        }
        return Response.ok(backing).build();
    }

    public byte[] serialize(RoaringBitmap bitmap) throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(byteOut);
        bitmap.serialize(out);
        return byteOut.toByteArray();
    }

    public Repository getRepository(String name) {
        String[] split = name.split("/");
        return split.length < 2 ? null : repositoryService.getBySlug(split[0], split[1]);
    }

    public Permission getRepositoryPermission(String permission) {
        Permission perm = null;
        try {
            perm = Permission.valueOf("REPO_" + permission.toUpperCase());
        } catch (IllegalArgumentException ignored) {
        }
        return perm;
    }
}
