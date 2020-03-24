package com.sourcegraph.permission;

import com.atlassian.bitbucket.permission.Permission;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositorySearchRequest;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.bitbucket.user.*;
import com.atlassian.bitbucket.util.*;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.sourcegraph.rest.Status;
import org.roaringbitmap.RoaringBitmap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
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

    @Autowired
    public PermissionRouter(RepositoryService repositoryService, UserManager userManager, UserService userService, SecurityService securityService) {
        PermissionRouter.repositoryService = repositoryService;
        PermissionRouter.userManager = userManager;
        PermissionRouter.userService = userService;
        PermissionRouter.securityService = securityService;
    }

    /**
     * The getUsersWithRepositoryPermission endpoint returns a roaring bitmap containing the IDs of all the users
     * that is granted the specified permission to the specified repository.
     * <p>
     * Ex. /permissions/users?repository=PROJECT_1/rep_1&permission=read
     */
    @GET
    @Path("/users")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getUsersWithRepositoryPermission(@Context HttpServletRequest request, @QueryParam("repository") String repo, @QueryParam("permission") String perm) throws IOException {
        UserProfile profile = userManager.getRemoteUser(request);
        if (profile == null || !userManager.isSystemAdmin(profile.getUserKey())) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Repository repository = getRepository(repo);
        if (repository == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("No such repository: " + repo).build();
        }

        Permission permission = getRepositoryPermission(perm);
        if (permission == null) {
            return Response.status(Status.UNPROCESSABLE_ENTITY)
                    .entity("Invalid permission: " + perm + " | Available options: admin, read, write").build();
        }

        RoaringBitmap bitmap = new RoaringBitmap();

        UserSearchRequest.Builder builder = new UserSearchRequest.Builder();
        builder.repositoryPermission(repository, permission);
        UserSearchRequest search = builder.build();

        PageProvider<ApplicationUser> pager = (pageRequest) -> userService.search(search, pageRequest);
        // The size of each page in the paginated search is 1000. This is the maximum sized page.
        for (ApplicationUser user : PageUtils.toIterable(pager, 1000)) {
            bitmap.add(user.getId());
        }

        byte[] backing = serialize(bitmap);
        return backing != null ?
                Response.ok(backing).header("X-Debug-Count", bitmap.getCardinality()).build() :
                Response.serverError().entity("Failed roaring bitmap serialization").build();
    }

    /**
     * The getAccessibleRepositories endpoint returns a roaring bitmap containing the IDs of all the repositories
     * that a user is granted the specified permission for.
     * <p>
     * Ex. /permissions/repositories?user=user&permission=admin
     */
    @GET
    @Path("/repositories")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getAccessibleRepositories(@Context HttpServletRequest request, @QueryParam("permission") String perm) {
        UserProfile profile = userManager.getRemoteUser(request);
        if (profile == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        ApplicationUser user = userService.getUserByName(profile.getUsername());
        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("No such user: " + profile.getUsername()).build();
        }

        Permission permission = getRepositoryPermission(perm);
        if (permission == null) {
            return Response.status(Status.UNPROCESSABLE_ENTITY)
                    .entity("Invalid permission: " + perm + " | Available options: admin, read, write").build();
        }

        EscalatedSecurityContext context = securityService.impersonating(user, "Repository Search");
        RoaringBitmap bitmap = context.call(() -> {
            RoaringBitmap temp = new RoaringBitmap();

            RepositorySearchRequest.Builder builder = new RepositorySearchRequest.Builder();
            builder.permission(permission);
            RepositorySearchRequest search = builder.build();

            PageProvider<Repository> pager = (pageRequest) -> repositoryService.search(search, pageRequest);
            // The service always only returns the configured `page.max.repositories` but `hasNextPage` is correctly true
            // see here https://confluence.atlassian.com/bitbucketserver058/bitbucket-server-config-properties-947168200.html#paging
            // for configuring that value. Higher values usually result in better performance.
            for (Repository repository : PageUtils.toIterable(pager, PageRequest.MAX_PAGE_LIMIT)) {
                temp.add(repository.getId());
            }
            return temp;
        });

        byte[] backing = serialize(bitmap);
        return backing != null ?
                Response.ok(backing)
                        .header("X-Debug-Count", bitmap.getCardinality())
                        .build() :
                Response.serverError().entity("Failed roaring bitmap serialization").build();
    }

    public byte[] serialize(RoaringBitmap bitmap) {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(byteOut);
        try {
            bitmap.serialize(out);
            return byteOut.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    public Repository getRepository(String name) {
        String[] split = name.split("/");
        return split.length < 2 ? null : repositoryService.getBySlug(split[0], split[1]);
    }

    public Permission getRepositoryPermission(String permission) {
        try {
            return Permission.valueOf("REPO_" + permission.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
