import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";

export function useDevices() {
  return useQuery({
    queryKey: ["user", "devices"],
    queryFn: () => unwrap(typedApi.GET("/api/user/devices")),
  });
}

export function useUpdateDevice() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, name }: { id: number; name: string }) => {
      await unwrap(
        typedApi.PUT("/api/user/devices/{id}", {
          params: { path: { id: String(id) } },
          body: { name },
        }),
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["user", "devices"] });
    },
  });
}

export function useDeleteDevice() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => {
      await unwrap(
        typedApi.DELETE("/api/user/devices/{id}", {
          params: { path: { id: String(id) } },
        }),
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["user", "devices"] });
    },
  });
}

export function useUpdateDevicePreferences() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      id,
      consoleShaders,
    }: {
      id: number;
      consoleShaders: Record<string, string>;
    }) => {
      await unwrap(
        typedApi.PUT("/api/user/devices/{id}/preferences", {
          params: { path: { id: String(id) } },
          body: { consoleShaders },
        }),
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["user", "devices"] });
    },
  });
}

export function useAdminUserDevices(userId: string) {
  return useQuery({
    queryKey: ["admin", "users", userId, "devices"],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/admin/users/{id}/devices", {
          params: { path: { id: userId } },
        }),
      ),
    enabled: !!userId,
  });
}
