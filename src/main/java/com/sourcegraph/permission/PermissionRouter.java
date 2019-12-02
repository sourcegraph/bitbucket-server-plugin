package com.sourcegraph.permission;

import com.atlassian.bitbucket.permission.Permission;
import com.atlassian.bitbucket.permission.PermissionService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.bitbucket.user.DetailedUser;
import com.atlassian.bitbucket.user.UserAdminService;
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
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

@Path("/permissions")
@Component
public class PermissionRouter {
    @ComponentImport
    private static UserAdminService userAdminService;
    @ComponentImport
    private static RepositoryService repositoryService;
    @ComponentImport
    private static UserManager userManager;
    @ComponentImport
    private static PermissionService permissionService;

    public PermissionRouter(UserAdminService users, RepositoryService repositoryService, UserManager userManager, PermissionService permissionService) {
        PermissionRouter.userAdminService = users;
        PermissionRouter.repositoryService = repositoryService;
        PermissionRouter.userManager = userManager;
        PermissionRouter.permissionService = permissionService;
    }

    @GET
    @Path("/users")
    public Response getUsersWithRepositoryPermission(@Context HttpServletRequest request, @QueryParam("repository") String repository, @QueryParam("permission") String permission) throws IOException {
        UserProfile profile = userManager.getRemoteUser(request);
        if (profile == null || !userManager.isSystemAdmin(profile.getUserKey())) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        String[] split = repository.split("/");
        if (split.length < 2) {
            return Response.status(Status.UNPROCESSABLE_ENTITY).build();
        }

        Repository repo = repositoryService.getBySlug(split[0], split[1]);
        if (repo == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("No such project: " + repository).build();
        }

        Permission perm;
        try {
            perm = Permission.valueOf("REPO_" + permission.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Response.status(Status.UNPROCESSABLE_ENTITY).build();
        }

        RoaringBitmap bitmap = new RoaringBitmap();
        PageRequest pageRequest = new PageRequestImpl(0, 100);
        do {
            Page<DetailedUser> page = userAdminService.findUsers(pageRequest);
            for (DetailedUser user : page.getValues()) {
                if (permissionService.hasRepositoryPermission(user, repo, perm)) {
                    bitmap.flip(user.getId());
                }
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

    public byte[] serialize(RoaringBitmap bitmap) throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(byteOut);
        bitmap.serialize(out);
        return byteOut.toByteArray();
    }
}
