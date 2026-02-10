import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { Device } from "@/types/api";

export function useDevices() {
  return useQuery({
    queryKey: ["user", "devices"],
    queryFn: () => api.get<Device[]>("/user/devices"),
  });
}

export function useUpdateDevice() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, name }: { id: number; name: string }) => {
      await api.put(`/user/devices/${id}`, { name });
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
      await api.delete(`/user/devices/${id}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["user", "devices"] });
    },
  });
}

export function useUpdateDevicePreferences() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, consoleShaders }: { id: number; consoleShaders: Record<string, string> }) => {
      await api.put(`/user/devices/${id}/preferences`, { consoleShaders });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["user", "devices"] });
    },
  });
}

export function useAdminUserDevices(userId: string) {
  return useQuery({
    queryKey: ["admin", "users", userId, "devices"],
    queryFn: () => api.get<Device[]>(`/admin/users/${userId}/devices`),
    enabled: !!userId,
  });
}
