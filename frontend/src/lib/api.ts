const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

export function getToken(): string | null {
  if (typeof window !== 'undefined') {
    return localStorage.getItem('token');
  }
  return null;
}

export function setToken(token: string): void {
  if (typeof window !== 'undefined') {
    localStorage.setItem('token', token);
  }
}

export function removeToken(): void {
  if (typeof window !== 'undefined') {
    localStorage.removeItem('token');
  }
}

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> || {}),
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 35000);

  try {
    const res = await fetch(`${API_BASE}${endpoint}`, {
      ...options,
      headers,
      signal: options.signal || controller.signal,
    });
    clearTimeout(timeoutId);

    const text = await res.text();
    let json: any = null;
    try {
      json = JSON.parse(text);
    } catch {
      // Response was plain text or HTML
    }

    if (!res.ok || (json && json.success === false)) {
      const errorMsg = (json && json.message) || (text && text.length < 200 ? text : `Request failed with status ${res.status}`);
      throw new Error(errorMsg);
    }

    return (json ? json.data : null) as T;
  } catch (err: any) {
    clearTimeout(timeoutId);
    if (err.name === 'AbortError') {
      throw new Error('Server request timed out. Please check if backend is running.');
    }
    throw err;
  }
}

export const api = {
  auth: {
    login: (data: any) => request<any>('/auth/login', { method: 'POST', body: JSON.stringify(data) }),
    register: (data: any) => request<any>('/auth/register', { method: 'POST', body: JSON.stringify(data) }),
    me: () => request<any>('/auth/me'),
  },
  workspaces: {
    list: () => request<any[]>('/workspaces'),
    create: (data: any) => request<any>('/workspaces', { method: 'POST', body: JSON.stringify(data) }),
  },
  repositories: {
    list: (workspaceId: number) => request<any[]>(`/workspaces/${workspaceId}/repositories`),
    get: (workspaceId: number, repoId: number) => request<any>(`/workspaces/${workspaceId}/repositories/${repoId}`),
    connect: (workspaceId: number, data: any) =>
      request<any>(`/workspaces/${workspaceId}/repositories`, { method: 'POST', body: JSON.stringify(data) }),
    updateConfig: (workspaceId: number, repoId: number, data: any) =>
      request<any>(`/workspaces/${workspaceId}/repositories/${repoId}/config`, { method: 'PUT', body: JSON.stringify(data) }),
    detect: (workspaceId: number, repoId: number) =>
      request<any>(`/workspaces/${workspaceId}/repositories/${repoId}/detect`, { method: 'POST' }),
  },
  jobs: {
    listWorkspaceJobs: (workspaceId: number) => request<any[]>(`/workspaces/${workspaceId}/jobs`),
    listRepoJobs: (workspaceId: number, repoId: number) =>
      request<any[]>(`/workspaces/${workspaceId}/repositories/${repoId}/jobs`),
    trigger: (workspaceId: number, repoId: number, data?: any) =>
      request<any>(`/workspaces/${workspaceId}/repositories/${repoId}/jobs/run`, {
        method: 'POST',
        body: JSON.stringify(data || {}),
      }),
    getDetail: (workspaceId: number, jobId: number) => request<any>(`/workspaces/${workspaceId}/jobs/${jobId}`),
    cancel: (workspaceId: number, jobId: number) =>
      request<any>(`/workspaces/${workspaceId}/jobs/${jobId}/cancel`, { method: 'POST' }),
  },
  github: {
    listRepos: (workspaceId: number, username: string, token?: string) => {
      let endpoint = `/workspaces/${workspaceId}/github/repos?username=${encodeURIComponent(username)}`;
      const headers: Record<string, string> = {};
      if (token && token.trim()) {
        const cleanToken = token.trim();
        headers['X-GitHub-Token'] = cleanToken;
        endpoint += `&token=${encodeURIComponent(cleanToken)}`;
      }
      return request<any[]>(endpoint, { headers });
    },
    importRepo: (workspaceId: number, data: any) =>
      request<any>(`/workspaces/${workspaceId}/github/import`, { method: 'POST', body: JSON.stringify(data) }),
  },
  fix: {
    attempt: (workspaceId: number, jobId: number) =>
      request<any>(`/workspaces/${workspaceId}/jobs/${jobId}/fix`, { method: 'POST' }),
    getLatest: (workspaceId: number, jobId: number) =>
      request<any>(`/workspaces/${workspaceId}/jobs/${jobId}/fix`),
    openPr: (workspaceId: number, jobId: number, fixId: number) =>
      request<any>(`/workspaces/${workspaceId}/jobs/${jobId}/fix/${fixId}/pr`, { method: 'POST' }),
  },
  reports: {
    get: (workspaceId: number, jobId: number) => request<any>(`/workspaces/${workspaceId}/jobs/${jobId}/report`),
  },
};
