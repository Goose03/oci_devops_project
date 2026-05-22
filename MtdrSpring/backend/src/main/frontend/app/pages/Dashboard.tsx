import { useState, useEffect } from 'react';
import { Task, Activity, Sprint, Project } from '../types';
import { useAuth } from '../context/AuthContext';
import { useTeam } from '../context/TeamContext';
import {
  analyticsApi,
  projectsApi,
  sprintsApi,
  VelocityPoint,
  PriorityPoint,
  WorkedHoursPoint,
  TaskDistributionPoint,
  SprintKpiPoint,
} from '../services/api';

// VelocityPoint.week is reused as sprint name in sprint-based data
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '../components/ui/card';
import { Badge } from '../components/ui/badge';
import { Button } from '../components/ui/button';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Textarea } from '../components/ui/textarea';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../components/ui/select';
import {
  CheckCircle2,
  Clock,
  TrendingUp,
  Target,
  Plus,
  UserPlus,
  Loader2,
  BarChart3,
  PieChartIcon,
  Users,
  Zap,
  FolderKanban,
  ListChecks,
} from 'lucide-react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
} from 'recharts';

// ── Chart color palette ────────────────────────────────────────────────────────
const COLORS = ['#30c2b7', '#70e1bf', '#f97316', '#3b82f6', '#a855f7', '#eab308', '#ef4444', '#6366f1'];

const PRIORITY_COLORS: Record<string, string> = {
  low: '#30c2b7',
  medium: '#eab308',
  high: '#f97316',
  critical: '#ef4444',
};

// ── Custom pie label renderer ─────────────────────────────────────────────────
const renderPieLabel = ({
  cx, cy, midAngle, innerRadius, outerRadius, percent,
}: any) => {
  if (percent < 0.04) return null;
  const RADIAN = Math.PI / 180;
  const radius = innerRadius + (outerRadius - innerRadius) * 0.55;
  const x = cx + radius * Math.cos(-midAngle * RADIAN);
  const y = cy + radius * Math.sin(-midAngle * RADIAN);
  return (
    <text x={x} y={y} fill="white" textAnchor="middle" dominantBaseline="central" fontSize={11} fontWeight={600}>
      {`${(percent * 100).toFixed(0)}%`}
    </text>
  );
};

// ── Shorten "2026-W14" → "W14" for axis labels ────────────────────────────────
function shortWeek(label: string): string {
  return label.replace(/^\d{4}-/, '');
}

// ── Loading skeleton ───────────────────────────────────────────────────────────
function ChartSkeleton() {
  return (
    <div className="flex items-center justify-center h-56 text-gray-400">
      <Loader2 className="w-6 h-6 animate-spin mr-2" />
      <span className="text-sm">Loading chart…</span>
    </div>
  );
}

// ── Empty state ────────────────────────────────────────────────────────────────
function ChartEmpty({ message }: { message: string }) {
  return (
    <div className="flex items-center justify-center h-56 text-gray-400">
      <p className="text-sm text-center">{message}</p>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────────────

interface DashboardProps {
  tasks: Task[];
  activities: Activity[];
  sprints: Sprint[];
  projects: Project[];
  isLoading?: boolean;
}

export function Dashboard({
  tasks,
  sprints,
  projects,
  isLoading = false,
}: DashboardProps) {
  const { user, isAuthenticated } = useAuth();
  const { teams, createTeam, joinTeam, isLoading: isTeamLoading } = useTeam();

  // ── Team dialogs ──────────────────────────────────────────────
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
  const [isJoinDialogOpen, setIsJoinDialogOpen] = useState(false);
  const [createFormData, setCreateFormData] = useState({ name: '', description: '' });
  const [joinCode, setJoinCode] = useState('');

  // ── Analytics state ────────────────────────────────────────────
  const [velocityData, setVelocityData] = useState<VelocityPoint[]>([]);
  const [priorityData, setPriorityData] = useState<PriorityPoint[]>([]);
  const [workedHoursData, setWorkedHoursData] = useState<WorkedHoursPoint[]>([]);
  const [distributionData, setDistributionData] = useState<TaskDistributionPoint[]>([]);
  const [velocityBySprintData, setVelocityBySprintData] = useState<VelocityPoint[]>([]);
  const [workedHoursBySprintData, setWorkedHoursBySprintData] = useState<WorkedHoursPoint[]>([]);
  const [tasksBySprintData, setTasksBySprintData] = useState<WorkedHoursPoint[]>([]);
  const [analyticsLoading, setAnalyticsLoading] = useState(false);
  const [analyticsError, setAnalyticsError] = useState<string | null>(null);

  // ── KPI filter state ───────────────────────────────────────────
  const [allProjects, setAllProjects] = useState<Project[]>([]);
  const [sprintsForProject, setSprintsForProject] = useState<Sprint[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string>('');
  const [selectedSprintId, setSelectedSprintId] = useState<string>('');
  const [sprintKpi, setSprintKpi] = useState<SprintKpiPoint | null>(null);
  const [kpiLoading, setKpiLoading] = useState(false);

  // ── Stats (computed from real task props) ──────────────────────
  const inProgressTasks = tasks.filter((t) => t.status === 'in-progress');
  const doneTasks = tasks.filter((t) => t.status === 'done');
  const totalExpected = tasks.reduce((s, t) => s + (t.storyPoints || 0), 0);
  const totalWorked = tasks.reduce((s, t) => s + (t.workedHours || 0), 0);
  const activeSprints = sprints.filter((s) => s.status === 'active');

  // ── Fetch analytics whenever project/sprint selection changes ──
  useEffect(() => {
    if (!isAuthenticated) return;
    const pid = selectedProjectId || undefined;
    const sid = selectedSprintId || undefined;
    setAnalyticsLoading(true);
    setAnalyticsError(null);
    const sprintCalls = selectedProjectId
      ? [
          analyticsApi.getVelocityBySprint(selectedProjectId),
          analyticsApi.getWorkedHoursBySprint(selectedProjectId),
          analyticsApi.getTasksBySprint(selectedProjectId),
        ]
      : [Promise.resolve([]), Promise.resolve([]), Promise.resolve([])];

    Promise.all([
      analyticsApi.getVelocity(12, pid, sid),
      analyticsApi.getPriorityDistribution(pid, sid),
      analyticsApi.getWorkedHours(12, pid, sid),
      analyticsApi.getTaskDistribution(pid, sid),
      ...sprintCalls,
    ])
      .then(([vel, pri, wh, dist, velSprint, whSprint, tasksSprint]) => {
        setVelocityData(vel as VelocityPoint[]);
        setPriorityData(pri as PriorityPoint[]);
        setWorkedHoursData(wh as WorkedHoursPoint[]);
        setDistributionData(dist as TaskDistributionPoint[]);
        setVelocityBySprintData(velSprint as VelocityPoint[]);
        setWorkedHoursBySprintData(whSprint as WorkedHoursPoint[]);
        setTasksBySprintData(tasksSprint as WorkedHoursPoint[]);
      })
      .catch((err) => {
        console.error('Analytics fetch failed:', err);
        setAnalyticsError('Could not load analytics data. Make sure the backend is running.');
      })
      .finally(() => setAnalyticsLoading(false));
  }, [isAuthenticated, selectedProjectId, selectedSprintId]);

  // ── Load all projects once on mount ───────────────────────────
  useEffect(() => {
    if (!isAuthenticated) return;
    projectsApi.getAll()
      .then((data) => {
        setAllProjects(data);
        if (data.length > 0) setSelectedProjectId(data[0].id);
      })
      .catch((err) => console.error('Failed to load projects:', err));
  }, [isAuthenticated]);

  // ── Load sprints whenever selected project changes ─────────────
  useEffect(() => {
    if (!selectedProjectId) { setSprintsForProject([]); setSelectedSprintId(''); return; }
    sprintsApi.getByProject(selectedProjectId)
      .then((data) => {
        setSprintsForProject(data);
        // Auto-select the active sprint, or the first one
        const active = data.find((s) => s.status === 'active');
        setSelectedSprintId(active ? active.id : data.length > 0 ? data[0].id : '');
      })
      .catch((err) => console.error('Failed to load sprints:', err));
  }, [selectedProjectId]);

  // ── Fetch KPI whenever project or sprint selection changes ─────
  useEffect(() => {
    if (!selectedProjectId) { setSprintKpi(null); return; }
    setKpiLoading(true);
    analyticsApi.getSprintKpis(selectedProjectId, selectedSprintId || undefined)
      .then(setSprintKpi)
      .catch((err) => console.error('Failed to load KPIs:', err))
      .finally(() => setKpiLoading(false));
  }, [selectedProjectId, selectedSprintId]);

  // ── Sprint-based worked hours → stacked bar format ─────────────
  const workedHoursChartData = (() => {
    const sprints = Array.from(new Set(workedHoursBySprintData.map((d) => d.week)));
    const users = Array.from(new Set(workedHoursBySprintData.map((d) => d.userName)));
    return sprints.map((s) => {
      const row: Record<string, string | number> = { week: s };
      users.forEach((u) => {
        const entry = workedHoursBySprintData.find((d) => d.week === s && d.userName === u);
        row[u] = entry ? entry.hours : 0;
      });
      return row;
    });
  })();
  const workedHoursUsers = Array.from(new Set(workedHoursBySprintData.map((d) => d.userName)));

  const tasksBySprintChartData = (() => {
    const sprintNames = Array.from(new Set(tasksBySprintData.map((d) => d.week)));
    const members = Array.from(new Set(tasksBySprintData.map((d) => d.userName)));
    return sprintNames.map((s) => {
      const row: Record<string, string | number> = { sprint: s };
      members.forEach((u) => {
        const entry = tasksBySprintData.find((d) => d.week === s && d.userName === u);
        row[u] = entry ? entry.hours : 0;
      });
      return row;
    });
  })();
  const tasksBySprintMembers = Array.from(new Set(tasksBySprintData.map((d) => d.userName)));

  const velocityChartData = velocityBySprintData.length > 0
    ? velocityBySprintData.map((d) => ({ ...d, week: d.week }))
    : velocityData.map((d) => ({ ...d, week: shortWeek(d.week) }));
  const priorityChartData = priorityData.map((d) => ({
    ...d,
    name: d.priority.charAt(0).toUpperCase() + d.priority.slice(1),
    fill: PRIORITY_COLORS[d.priority] ?? '#6b7280',
  }));

  // ── Team handlers ──────────────────────────────────────────────
  const handleCreateTeam = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await createTeam(createFormData.name, createFormData.description);
      setIsCreateDialogOpen(false);
      setCreateFormData({ name: '', description: '' });
    } catch (err) {
      console.error('Failed to create team:', err);
    }
  };

  const handleJoinTeam = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await joinTeam(joinCode);
      setIsJoinDialogOpen(false);
      setJoinCode('');
    } catch (err) {
      console.error('Failed to join team:', err);
    }
  };

  return (
    <div className="p-6 space-y-6">
      {(isLoading || isTeamLoading) && (
        <div className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
          <div className="w-4 h-4 border-2 border-[#30c2b7] border-t-transparent rounded-full animate-spin" />
          Loading your data...
        </div>
      )}

      {/* ── Welcome banner ──────────────────────────────────── */}
      <div className="p-6 bg-linear-to-r from-[#30c2b7] to-[#70e1bf] rounded-xl text-white">
        <h1 className="text-3xl mb-1">Welcome back, {user?.name}! 👋</h1>
        <p className="text-[#96efc1]">Here's your team's performance at a glance</p>

        {teams.length === 0 && (
          <div className="mt-4 p-4 bg-white/10 backdrop-blur-sm rounded-lg border border-white/20">
            <p className="text-white mb-3">Get started by creating or joining a team</p>
            <div className="flex gap-3">
              <Button onClick={() => setIsCreateDialogOpen(true)} className="bg-white text-[#30c2b7] hover:bg-white/90">
                <Plus className="w-4 h-4 mr-2" />Create Team
              </Button>
              <Button onClick={() => setIsJoinDialogOpen(true)} variant="outline" className="border-white text-white hover:bg-white/10">
                <UserPlus className="w-4 h-4 mr-2" />Join Team
              </Button>
            </div>
          </div>
        )}
      </div>

      {/* ── KPI Filters: Project · Sprint · Week ────────────── */}
      <Card className="border-[#30c2b7]/30">
        <CardContent className="p-4">
          <div className="flex flex-wrap items-end gap-4">
            {/* Project selector */}
            <div className="flex-1 min-w-40">
              <p className="text-xs font-medium text-gray-500 dark:text-gray-400 mb-1.5 flex items-center gap-1">
                <FolderKanban className="w-3.5 h-3.5" /> Project
              </p>
              <Select value={selectedProjectId} onValueChange={(v) => { setSelectedProjectId(v); setSelectedSprintId(''); }}>
                <SelectTrigger className="h-9 text-sm">
                  <SelectValue placeholder="Select project…" />
                </SelectTrigger>
                <SelectContent>
                  {allProjects.length === 0
                    ? <SelectItem value="__none" disabled>No projects yet</SelectItem>
                    : allProjects.map((p) => (
                        <SelectItem key={p.id} value={p.id}>{p.name}</SelectItem>
                      ))
                  }
                </SelectContent>
              </Select>
            </div>

            {/* Sprint selector */}
            <div className="flex-1 min-w-40">
              <p className="text-xs font-medium text-gray-500 dark:text-gray-400 mb-1.5 flex items-center gap-1">
                <ListChecks className="w-3.5 h-3.5" /> Sprint
              </p>
              <Select
                value={selectedSprintId || '__all'}
                onValueChange={(v) => setSelectedSprintId(v === '__all' ? '' : v)}
                disabled={!selectedProjectId || sprintsForProject.length === 0}
              >
                <SelectTrigger className="h-9 text-sm">
                  <SelectValue placeholder={sprintsForProject.length === 0 ? 'No sprints' : 'All sprints'} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__all">All sprints</SelectItem>
                  {sprintsForProject.map((s) => (
                    <SelectItem key={s.id} value={s.id}>
                      {s.status === 'active' ? '🟢 ' : s.status === 'completed' ? '✅ ' : '📋 '}
                      {s.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

          </div>
        </CardContent>
      </Card>

      {/* ── Sprint KPI Summary Cards ─────────────────────────── */}
      {selectedProjectId && (
        <div>
          <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-3">
            {sprintKpi
              ? `${sprintKpi.projectName} — ${sprintKpi.sprintName}`
              : 'Sprint KPIs'}
          </h2>

          {kpiLoading ? (
            <div className="flex items-center gap-2 text-sm text-gray-400 py-4">
              <Loader2 className="w-4 h-4 animate-spin" /> Loading KPI data…
            </div>
          ) : sprintKpi ? (
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">

              {/* Hours Worked */}
              <Card className="border-t-4 border-t-[#30c2b7]">
                <CardContent className="p-5">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-xs text-gray-500 dark:text-gray-400 mb-0.5">Hours Worked</p>
                      <p className="text-2xl font-semibold dark:text-white">{sprintKpi.totalHoursWorked}h</p>
                      <p className="text-xs text-gray-400 mt-0.5">of {sprintKpi.totalHoursEstimated}h estimated</p>
                    </div>
                    <div className="w-10 h-10 bg-teal-100 dark:bg-teal-900/40 rounded-lg flex items-center justify-center">
                      <Clock className="w-5 h-5 text-[#30c2b7]" />
                    </div>
                  </div>
                </CardContent>
              </Card>

              {/* Tasks Completed */}
              <Card className="border-t-4 border-t-green-500">
                <CardContent className="p-5">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-xs text-gray-500 dark:text-gray-400 mb-0.5">Tasks Completed</p>
                      <p className="text-2xl font-semibold dark:text-white">{sprintKpi.completedTasks}</p>
                      <p className="text-xs text-gray-400 mt-0.5">of {sprintKpi.totalTasks} total</p>
                    </div>
                    <div className="w-10 h-10 bg-green-100 dark:bg-green-900/40 rounded-lg flex items-center justify-center">
                      <CheckCircle2 className="w-5 h-5 text-green-600 dark:text-green-400" />
                    </div>
                  </div>
                </CardContent>
              </Card>

              {/* Completion Rate */}
              <Card className="border-t-4 border-t-indigo-500">
                <CardContent className="p-5">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-xs text-gray-500 dark:text-gray-400 mb-0.5">Completion Rate</p>
                      <p className="text-2xl font-semibold dark:text-white">{sprintKpi.completionRate}%</p>
                      <div className="mt-1 h-1.5 w-24 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                        <div
                          className="h-full bg-indigo-500 rounded-full transition-all duration-500"
                          style={{ width: `${sprintKpi.completionRate}%` }}
                        />
                      </div>
                    </div>
                    <div className="w-10 h-10 bg-indigo-100 dark:bg-indigo-900/40 rounded-lg flex items-center justify-center">
                      <Target className="w-5 h-5 text-indigo-600 dark:text-indigo-400" />
                    </div>
                  </div>
                </CardContent>
              </Card>

              {/* Remaining Tasks */}
              <Card className="border-t-4 border-t-orange-400">
                <CardContent className="p-5">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-xs text-gray-500 dark:text-gray-400 mb-0.5">Remaining</p>
                      <p className="text-2xl font-semibold dark:text-white">
                        {sprintKpi.totalTasks - sprintKpi.completedTasks}
                      </p>
                      <p className="text-xs text-gray-400 mt-0.5">tasks in progress</p>
                    </div>
                    <div className="w-10 h-10 bg-orange-100 dark:bg-orange-900/40 rounded-lg flex items-center justify-center">
                      <TrendingUp className="w-5 h-5 text-orange-500" />
                    </div>
                  </div>
                </CardContent>
              </Card>

            </div>
          ) : (
            <p className="text-sm text-gray-400 py-2">Select a project to see KPI data.</p>
          )}
        </div>
      )}

      {/* ── Stats grid ──────────────────────────────────────── */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-xs text-gray-500 dark:text-gray-400 mb-0.5">Total Tasks</p>
                <p className="text-2xl font-semibold dark:text-white">{tasks.length}</p>
                <p className="text-xs text-gray-400 mt-0.5">{doneTasks.length} done</p>
              </div>
              <div className="w-10 h-10 bg-blue-100 dark:bg-blue-900/40 rounded-lg flex items-center justify-center">
                <CheckCircle2 className="w-5 h-5 text-blue-600 dark:text-blue-400" />
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-5">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-xs text-gray-500 dark:text-gray-400 mb-0.5">In Progress</p>
                <p className="text-2xl font-semibold dark:text-white">{inProgressTasks.length}</p>
                <p className="text-xs text-gray-400 mt-0.5">Active now</p>
              </div>
              <div className="w-10 h-10 bg-orange-100 dark:bg-orange-900/40 rounded-lg flex items-center justify-center">
                <Clock className="w-5 h-5 text-orange-600 dark:text-orange-400" />
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-5">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-xs text-gray-500 dark:text-gray-400 mb-0.5">Hours</p>
                <p className="text-2xl font-semibold dark:text-white">{totalWorked}h</p>
                <p className="text-xs text-gray-400 mt-0.5">of {totalExpected}h estimated</p>
              </div>
              <div className="w-10 h-10 bg-green-100 dark:bg-green-900/40 rounded-lg flex items-center justify-center">
                <TrendingUp className="w-5 h-5 text-green-600 dark:text-green-400" />
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-5">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-xs text-gray-500 dark:text-gray-400 mb-0.5">Active Sprints</p>
                <p className="text-2xl font-semibold dark:text-white">{activeSprints.length}</p>
                <p className="text-xs text-gray-400 mt-0.5">Across {projects.length} projects</p>
              </div>
              <div className="w-10 h-10 bg-purple-100 dark:bg-purple-900/40 rounded-lg flex items-center justify-center">
                <Target className="w-5 h-5 text-purple-600 dark:text-purple-400" />
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* ── Chart section header + error banner ─────────────── */}
      <div className="flex items-center gap-2">
        <h2 className="text-lg font-semibold dark:text-white flex items-center gap-2">
          <BarChart3 className="w-5 h-5 text-[#30c2b7]" />
          Team Analytics Charts
        </h2>
      </div>

      {analyticsError && (
        <div className="p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg text-sm text-red-700 dark:text-red-400">
          {analyticsError}
        </div>
      )}

      {/* ── 2×2 KPI chart grid ───────────────────────────────── */}
      <div className="grid lg:grid-cols-2 gap-6">

        {/* ① Task Completion Velocity */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-base">
              <Zap className="w-4 h-4 text-[#30c2b7]" />
              Task Completion Velocity
              <Badge variant="outline" className="text-xs ml-auto font-normal text-gray-500">Created vs Completed / sprint</Badge>
            </CardTitle>
          </CardHeader>
          <CardContent>
            {analyticsLoading ? (
              <ChartSkeleton />
            ) : velocityChartData.length === 0 ? (
              <ChartEmpty message="No task data yet. Create tasks and come back later." />
            ) : (
              <ResponsiveContainer width="100%" height={220}>
                <BarChart data={velocityChartData} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
                  <XAxis dataKey="week" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
                  <Tooltip
                    contentStyle={{ borderRadius: 8, fontSize: 12 }}
formatter={(val: number, name: string) => [val, name]}                  />
                  <Legend iconSize={10} wrapperStyle={{ fontSize: 12 }} />
                  <Bar dataKey="created" name="Created" fill="#3b82f6" radius={[3, 3, 0, 0]} />
                  <Bar dataKey="completed" name="Completed" fill="#30c2b7" radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </CardContent>
        </Card>

        {/* ② Task Urgency Distribution */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-base">
              <PieChartIcon className="w-4 h-4 text-orange-500" />
              Task Urgency Distribution
              <Badge variant="outline" className="text-xs ml-auto font-normal text-gray-500">By priority level</Badge>
            </CardTitle>
          </CardHeader>
          <CardContent>
            {analyticsLoading ? (
              <ChartSkeleton />
            ) : priorityChartData.length === 0 ? (
              <ChartEmpty message="No tasks with priorities found." />
            ) : (
              <div className="flex items-center gap-4">
                <ResponsiveContainer width="60%" height={220}>
                  <PieChart>
                    <Pie
                      data={priorityChartData}
                      cx="50%"
                      cy="50%"
                      outerRadius={90}
                      dataKey="count"
                      labelLine={false}
                      label={renderPieLabel}
                    >
                      {priorityChartData.map((entry) => (
                        <Cell key={entry.priority} fill={entry.fill} />
                      ))}
                    </Pie>
                    <Tooltip
                      contentStyle={{ borderRadius: 8, fontSize: 12 }}
                      formatter={(val: number, _: string, props: any) => [val + ' tasks', props.payload.name]}
                    />
                  </PieChart>
                </ResponsiveContainer>
                <div className="flex flex-col gap-2">
                  {priorityChartData.map((d) => (
                    <div key={d.priority} className="flex items-center gap-2 text-sm">
                      <span className="w-3 h-3 rounded-full shrink-0" style={{ background: d.fill }} />
                      <span className="dark:text-gray-200">{d.name}</span>
                      <span className="ml-auto font-semibold dark:text-white">{d.count}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </CardContent>
        </Card>
            
        {/* ③ Worked Hours per User per Week */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-base">
              <Clock className="w-4 h-4 text-indigo-500" />
              Worked Hours per Team Member
              <Badge variant="outline" className="text-xs ml-auto font-normal text-gray-500">Stacked by sprint</Badge>
            </CardTitle>
          </CardHeader>
          <CardContent>
            {analyticsLoading ? (
              <ChartSkeleton />
            ) : workedHoursChartData.length === 0 ? (
              <ChartEmpty message="No worked hours logged yet. Update tasks with worked hours to see data here." />
            ) : (
              <ResponsiveContainer width="100%" height={220}>
                <BarChart data={workedHoursChartData} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
                  <XAxis dataKey="week" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} allowDecimals={false} unit="h" />
                  <Tooltip
                    contentStyle={{ borderRadius: 8, fontSize: 12 }}
                    formatter={(val: number, name: string) => [`${val}h`, name]}
                  />
                  <Legend iconSize={10} wrapperStyle={{ fontSize: 12 }} />
                  {workedHoursUsers.map((name, i) => (
  <Bar
    key={name}
    dataKey={name}
    fill={COLORS[i % COLORS.length]}
    radius={[3, 3, 0, 0]}
  />
))}
                </BarChart>
              </ResponsiveContainer>
            )}
          </CardContent>
        </Card>

        {/* ④ Tasks per Member per Sprint */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-base">
              <Users className="w-4 h-4 text-[#30c2b7]" />
              Tasks per Member per Sprint
              <Badge variant="outline" className="text-xs ml-auto font-normal text-gray-500">Stacked by member</Badge>
            </CardTitle>
          </CardHeader>
          <CardContent>
            {analyticsLoading ? (
              <ChartSkeleton />
            ) : tasksBySprintChartData.length === 0 ? (
              <ChartEmpty message="No sprint data yet. Select a project with assigned tasks to see the breakdown." />
            ) : (
              <ResponsiveContainer width="100%" height={220}>
                <BarChart data={tasksBySprintChartData} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
                  <XAxis dataKey="sprint" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
                  <Tooltip
                    contentStyle={{ borderRadius: 8, fontSize: 12 }}
                    formatter={(val: number, name: string) => [val + ' tasks', name]}
                  />
                  <Legend iconSize={10} wrapperStyle={{ fontSize: 12 }} />
                  {tasksBySprintMembers.map((name, i) => (
                    <Bar
                      key={name}
                      dataKey={name}
                      fill={COLORS[i % COLORS.length]}
                      radius={[3, 3, 0, 0]}
                    />
                  ))}
                </BarChart>
              </ResponsiveContainer>
            )}
          </CardContent>
        </Card>

      </div>

      {/* ── Create Team Dialog ───────────────────────────────── */}
      <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
        <DialogContent className="bg-white dark:bg-gray-900 sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Create New Team</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleCreateTeam} className="space-y-4">
            <div>
              <Label htmlFor="teamName">Team Name</Label>
              <Input
                id="teamName"
                value={createFormData.name}
                onChange={(e) => setCreateFormData({ ...createFormData, name: e.target.value })}
                placeholder="Engineering Team"
                required
              />
            </div>
            <div>
              <Label htmlFor="teamDescription">Description</Label>
              <Textarea
                id="teamDescription"
                value={createFormData.description}
                onChange={(e) => setCreateFormData({ ...createFormData, description: e.target.value })}
                placeholder="Describe your team..."
                rows={3}
                required
              />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="outline" onClick={() => setIsCreateDialogOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isTeamLoading} className="bg-[#30c2b7] hover:bg-[#28a89e] text-white">
                {isTeamLoading ? 'Creating…' : 'Create Team'}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* ── Join Team Dialog ─────────────────────────────────── */}
      <Dialog open={isJoinDialogOpen} onOpenChange={setIsJoinDialogOpen}>
        <DialogContent className="bg-white dark:bg-gray-900 sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Join a Team</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleJoinTeam} className="space-y-4">
            <div>
              <Label htmlFor="joinCode">Team Join Code</Label>
              <Input
                id="joinCode"
                value={joinCode}
                onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
                placeholder="Enter 6-character code"
                maxLength={6}
                className="uppercase tracking-widest text-center text-lg"
                required
              />
              <p className="text-sm text-gray-500 mt-1">Ask your team admin for the join code.</p>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="outline" onClick={() => setIsJoinDialogOpen(false)}>
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={isTeamLoading || joinCode.length !== 6}
                className="bg-[#30c2b7] hover:bg-[#28a89e] text-white"
              >
                {isTeamLoading ? 'Joining…' : 'Join Team'}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
