/**
 * API Service Layer
 *
 * Uses a relative path so the same build works in both:
 *   - Development: Vite proxies /api → http://localhost:8080
 *   - Production (Docker): Spring Boot serves static files AND the API on the same port
 */

import { User, Team, Project, Task, Sprint, TeamMember, UserRole } from '../types';

const API_BASE_URL = '/api';

// ─── helpers ────────────────────────────────────────────────────────────────

function getToken(): string {
  return localStorage.getItem('auth_token') ?? '';
}

function authHeaders(): Record<string, string> {
  const token = getToken();
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    throw new Error(`HTTP ${res.status}: ${text}`);
  }
  // Handle responses with no body (204 No Content, or 200 with empty body from DELETE)
  const text = await res.text();
  if (!text) return undefined as unknown as T;
  return JSON.parse(text) as T;
}

// ==================== AUTH API ====================

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  user: User;
  token: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}

export interface RegisterResponse {
  user: User;
  token: string;
}

export const authApi = {
  login: async (data: LoginRequest): Promise<LoginResponse> => {
    const res = await fetch(`${API_BASE_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return handleResponse<LoginResponse>(res);
  },

  register: async (data: RegisterRequest): Promise<RegisterResponse> => {
    const res = await fetch(`${API_BASE_URL}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return handleResponse<RegisterResponse>(res);
  },

  logout: async (): Promise<void> => {
    await fetch(`${API_BASE_URL}/auth/logout`, {
      method: 'POST',
      headers: authHeaders(),
    });
  },
};

// ==================== TEAMS API ====================

export interface CreateTeamRequest {
  name: string;
  description: string;
}

export interface CreateTeamResponse {
  team: Team;
  joinCode: string;
}

export interface JoinTeamRequest {
  joinCode: string;
}

export interface JoinTeamResponse {
  team: Team;
  success: boolean;
}

export const teamsApi = {
  create: async (data: CreateTeamRequest): Promise<CreateTeamResponse> => {
    const res = await fetch(`${API_BASE_URL}/teams/create`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(data),
    });
    return handleResponse<CreateTeamResponse>(res);
  },

  join: async (data: JoinTeamRequest): Promise<JoinTeamResponse> => {
    const res = await fetch(`${API_BASE_URL}/teams/join`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(data),
    });
    return handleResponse<JoinTeamResponse>(res);
  },

  getMembers: async (teamId: string): Promise<TeamMember[]> => {
    const res = await fetch(`${API_BASE_URL}/teams/${teamId}/members`, {
      headers: authHeaders(),
    });
    return handleResponse<TeamMember[]>(res);
  },

  getById: async (teamId: string): Promise<Team> => {
    const res = await fetch(`${API_BASE_URL}/teams/${teamId}`, {
      headers: authHeaders(),
    });
    return handleResponse<Team>(res);
  },

  getAll: async (): Promise<Team[]> => {
    const res = await fetch(`${API_BASE_URL}/teams`, {
      headers: authHeaders(),
    });
    return handleResponse<Team[]>(res);
  },

  getTasks: async (teamId: string): Promise<Task[]> => {
    const res = await fetch(`${API_BASE_URL}/teams/${teamId}/tasks`, {
      headers: authHeaders(),
    });
    return handleResponse<Task[]>(res);
  },

  getSprints: async (teamId: string): Promise<Sprint[]> => {
    const res = await fetch(`${API_BASE_URL}/teams/${teamId}/sprints`, {
      headers: authHeaders(),
    });
    return handleResponse<Sprint[]>(res);
  },

  inviteMember: async (teamId: string, email: string, role: UserRole): Promise<TeamMember> => {
    const res = await fetch(`${API_BASE_URL}/teams/${teamId}/invite`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ email, role }),
    });
    return handleResponse<TeamMember>(res);
  },
};

// ==================== TASKS API ====================

export interface CreateTaskRequest {
  title: string;
  description: string;
  priority: Task['priority'];
  type: Task['type'];
  assigneeId?: string;
  storyPoints?: number;
  sprintId?: string;
  projectId: string;
  tags?: string[];
}

export interface UpdateTaskRequest {
  title?: string;
  description?: string;
  status?: Task['status'];
  priority?: Task['priority'];
  type?: Task['type'];
  assigneeId?: string;
  storyPoints?: number;
  workedHours?: number;
  sprintId?: string;
  tags?: string[];
}

export const tasksApi = {
  getAll: async (filters?: {
    projectId?: string;
    sprintId?: string;
    assigneeId?: string;
    status?: Task['status'];
  }): Promise<Task[]> => {
    const params = new URLSearchParams(
      Object.fromEntries(
        Object.entries(filters ?? {}).filter(([, v]) => v !== undefined) as [string, string][]
      )
    );
    const res = await fetch(`${API_BASE_URL}/tasks?${params}`, {
      headers: authHeaders(),
    });
    return handleResponse<Task[]>(res);
  },

  getById: async (taskId: string): Promise<Task> => {
    const res = await fetch(`${API_BASE_URL}/tasks/${taskId}`, {
      headers: authHeaders(),
    });
    return handleResponse<Task>(res);
  },

  create: async (data: CreateTaskRequest): Promise<Task> => {
    const res = await fetch(`${API_BASE_URL}/tasks`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(data),
    });
    return handleResponse<Task>(res);
  },

  update: async (taskId: string, data: UpdateTaskRequest): Promise<Task> => {
    const res = await fetch(`${API_BASE_URL}/tasks/${taskId}`, {
      method: 'PUT',
      headers: authHeaders(),
      body: JSON.stringify(data),
    });
    return handleResponse<Task>(res);
  },

  delete: async (taskId: string): Promise<void> => {
    const res = await fetch(`${API_BASE_URL}/tasks/${taskId}`, {
      method: 'DELETE',
      headers: authHeaders(),
    });
    return handleResponse<void>(res);
  },
};

// ==================== PROJECTS API ====================

export interface UpdateProjectRequest {
  name?: string;
  description?: string;
  key?: string;
  status?: string;
}

export const projectsApi = {
  getAll: async (): Promise<Project[]> => {
    const res = await fetch(`${API_BASE_URL}/projects`, {
      headers: authHeaders(),
    });
    return handleResponse<Project[]>(res);
  },

  getByTeam: async (teamId: string): Promise<Project[]> => {
    const res = await fetch(`${API_BASE_URL}/teams/${teamId}/projects`, {
      headers: authHeaders(),
    });
    return handleResponse<Project[]>(res);
  },

  getById: async (projectId: string): Promise<Project> => {
    const res = await fetch(`${API_BASE_URL}/projects/${projectId}`, {
      headers: authHeaders(),
    });
    return handleResponse<Project>(res);
  },

  create: async (data: Partial<Project> & { teamId: string }): Promise<Project> => {
    const res = await fetch(`${API_BASE_URL}/projects`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(data),
    });
    return handleResponse<Project>(res);
  },

  update: async (projectId: string, data: UpdateProjectRequest): Promise<Project> => {
    const res = await fetch(`${API_BASE_URL}/projects/${projectId}`, {
      method: 'PUT',
      headers: authHeaders(),
      body: JSON.stringify(data),
    });
    return handleResponse<Project>(res);
  },

  delete: async (projectId: string): Promise<void> => {
    const res = await fetch(`${API_BASE_URL}/projects/${projectId}`, {
      method: 'DELETE',
      headers: authHeaders(),
    });
    return handleResponse<void>(res);
  },
};

// ==================== SPRINTS API ====================

// ==================== ANALYTICS API ====================

export interface VelocityPoint {
  week: string;
  created: number;
  completed: number;
}

export interface PriorityPoint {
  priority: string;
  count: number;
}

export interface WorkedHoursPoint {
  week: string;
  userName: string;
  hours: number;
}

export interface TaskDistributionPoint {
  userId: string;
  userName: string;
  taskCount: number;
  percentage: number;
}

export interface SprintKpiPoint {
  sprintId: string | null;
  sprintName: string;
  projectId: string;
  projectName: string;
  totalTasks: number;
  completedTasks: number;
  totalHoursWorked: number;
  totalHoursEstimated: number;
  completionRate: number;
}

export const analyticsApi = {
  getVelocity: async (weeks = 8, projectId?: string, sprintId?: string): Promise<VelocityPoint[]> => {
    const p = new URLSearchParams({ weeks: String(weeks) });
    if (projectId) p.set('projectId', projectId);
    if (sprintId) p.set('sprintId', sprintId);
    const res = await fetch(`${API_BASE_URL}/analytics/velocity?${p}`, { headers: authHeaders() });
    return handleResponse<VelocityPoint[]>(res);
  },

  getPriorityDistribution: async (projectId?: string, sprintId?: string): Promise<PriorityPoint[]> => {
    const p = new URLSearchParams();
    if (projectId) p.set('projectId', projectId);
    if (sprintId) p.set('sprintId', sprintId);
    const query = p.toString() ? `?${p}` : '';
    const res = await fetch(`${API_BASE_URL}/analytics/priority${query}`, { headers: authHeaders() });
    return handleResponse<PriorityPoint[]>(res);
  },

  getWorkedHours: async (weeks = 8, projectId?: string, sprintId?: string): Promise<WorkedHoursPoint[]> => {
    const p = new URLSearchParams({ weeks: String(weeks) });
    if (projectId) p.set('projectId', projectId);
    if (sprintId) p.set('sprintId', sprintId);
    const res = await fetch(`${API_BASE_URL}/analytics/worked-hours?${p}`, { headers: authHeaders() });
    return handleResponse<WorkedHoursPoint[]>(res);
  },

  getTaskDistribution: async (projectId?: string, sprintId?: string): Promise<TaskDistributionPoint[]> => {
    const p = new URLSearchParams();
    if (projectId) p.set('projectId', projectId);
    if (sprintId) p.set('sprintId', sprintId);
    const query = p.toString() ? `?${p}` : '';
    const res = await fetch(`${API_BASE_URL}/analytics/task-distribution${query}`, { headers: authHeaders() });
    return handleResponse<TaskDistributionPoint[]>(res);
  },

  getVelocityBySprint: async (projectId: string): Promise<VelocityPoint[]> => {
    const res = await fetch(`${API_BASE_URL}/analytics/velocity-by-sprint?projectId=${projectId}`, { headers: authHeaders() });
    return handleResponse<VelocityPoint[]>(res);
  },

  getWorkedHoursBySprint: async (projectId: string): Promise<WorkedHoursPoint[]> => {
    const res = await fetch(`${API_BASE_URL}/analytics/worked-hours-by-sprint?projectId=${projectId}`, { headers: authHeaders() });
    return handleResponse<WorkedHoursPoint[]>(res);
  },

  getTasksBySprint: async (projectId: string): Promise<WorkedHoursPoint[]> => {
    const res = await fetch(`${API_BASE_URL}/analytics/tasks-by-sprint?projectId=${projectId}`, { headers: authHeaders() });
    return handleResponse<WorkedHoursPoint[]>(res);
  },

  /** GET /api/analytics/kpis?projectId=X&sprintId=Y (sprintId optional) */
  getSprintKpis: async (projectId: string, sprintId?: string): Promise<SprintKpiPoint> => {
    const params = new URLSearchParams({ projectId });
    if (sprintId) params.append('sprintId', sprintId);
    const res = await fetch(`${API_BASE_URL}/analytics/kpis?${params}`, {
      headers: authHeaders(),
    });
    return handleResponse<SprintKpiPoint>(res);
  },
};

export const sprintsApi = {
  /** Get sprints for a specific project */
  getByProject: async (projectId: string): Promise<Sprint[]> => {
    const res = await fetch(`${API_BASE_URL}/sprints?projectId=${projectId}`, {
      headers: authHeaders(),
    });
    return handleResponse<Sprint[]>(res);
  },

  /** Get all sprints for the authenticated user (across all their projects) */
  getAll: async (): Promise<Sprint[]> => {
    const res = await fetch(`${API_BASE_URL}/sprints`, {
      headers: authHeaders(),
    });
    return handleResponse<Sprint[]>(res);
  },

  create: async (data: Partial<Sprint> & { projectId: string }): Promise<Sprint> => {
    const res = await fetch(`${API_BASE_URL}/sprints`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(data),
    });
    return handleResponse<Sprint>(res);
  },
};

// ==================== AI API ====================
// Add this to your existing services/api.ts file or create a new aiInsightsApi.ts

export interface AIInsight {
  week: string;
  content: string;
  generatedAt: string;
}

export const aiInsightsApi = {
  /**
   * Fetch all AI insights from the backend
   */
  async getInsights(): Promise<AIInsight[]> {
    const response = await fetch('/api/ai/insights', {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        // Add authorization header if needed
        // 'Authorization': `Bearer ${getAuthToken()}`,
      },
    });

    if (!response.ok) {
      throw new Error(`Failed to fetch insights: ${response.statusText}`);
    }

    return response.json();
  },

  /**
   * Generate new AI insights based on current KPI data
   */
  async generateInsights(): Promise<AIInsight> {
    const response = await fetch('/api/ai/insights/generate', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    if (!response.ok) {
      throw new Error(`Failed to generate insights: ${response.statusText}`);
    }

    return response.json();
  },

  /**
   * Get insights for a specific week
   */
  async getInsightsByWeek(week: string): Promise<AIInsight | null> {
    const response = await fetch(`/api/ai/insights/week/${week}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    if (response.status === 404) {
      return null;
    }

    if (!response.ok) {
      throw new Error(`Failed to fetch insight for week ${week}: ${response.statusText}`);
    }

    return response.json();
  },
};