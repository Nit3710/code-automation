'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { api, getToken, removeToken } from '@/lib/api';
import {
  ShieldCheck,
  GitBranch,
  Terminal,
  Play,
  RotateCw,
  Clock,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  FileText,
  Bot,
  LogOut,
  FolderPlus,
  Settings,
  Copy,
  Check
} from 'lucide-react';

export default function DashboardPage() {
  const router = useRouter();
  const [user, setUser] = useState<any>(null);
  const [workspaces, setWorkspaces] = useState<any[]>([]);
  const [activeWorkspace, setActiveWorkspace] = useState<any>(null);
  const [repositories, setRepositories] = useState<any[]>([]);
  const [selectedRepo, setSelectedRepo] = useState<any>(null);
  const [jobs, setJobs] = useState<any[]>([]);
  const [selectedJobDetail, setSelectedJobDetail] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [isTriggering, setIsTriggering] = useState(false);
  const [copied, setCopied] = useState(false);

  // Connect repo modal state
  const [showConnectModal, setShowConnectModal] = useState(false);
  const [connectTab, setConnectTab] = useState<'github' | 'manual'>('github');
  const [githubUsername, setGithubUsername] = useState('Nit3710');
  const [githubRepos, setGithubRepos] = useState<any[]>([]);
  const [isFetchingGithub, setIsFetchingGithub] = useState(false);
  const [importingRepoName, setImportingRepoName] = useState<string | null>(null);

  // Manual fallback state
  const [repoName, setRepoName] = useState('');
  const [repoFullName, setRepoFullName] = useState('');
  const [cloneUrl, setCloneUrl] = useState('');
  const [defaultBranch, setDefaultBranch] = useState('main');

  // Config modal state
  const [showConfigModal, setShowConfigModal] = useState(false);
  const [editConfig, setEditConfig] = useState<any>({});

  // Active tab in Job Detail modal
  const [activeDetailTab, setActiveDetailTab] = useState<'logs' | 'ai' | 'fix' | 'report'>('logs');
  const [activeFix, setActiveFix] = useState<any>(null);
  const [isFixing, setIsFixing] = useState(false);
  const [isCreatingPr, setIsCreatingPr] = useState(false);

  useEffect(() => {
    const token = getToken();
    if (!token) {
      router.replace('/login');
      return;
    }
    initDashboard();
  }, []);

  const initDashboard = async () => {
    try {
      const currentUser = await api.auth.me();
      setUser(currentUser);

      const wsList = await api.workspaces.list();
      setWorkspaces(wsList);

      if (wsList.length > 0) {
        setActiveWorkspace(wsList[0]);
        await loadWorkspaceData(wsList[0].id);
      }
    } catch (err) {
      console.error('Failed to init dashboard:', err);
      removeToken();
      router.push('/login');
    } finally {
      setLoading(false);
    }
  };

  const loadWorkspaceData = async (workspaceId: number) => {
    try {
      const repos = await api.repositories.list(workspaceId);
      setRepositories(repos);
      if (repos.length > 0 && !selectedRepo) {
        setSelectedRepo(repos[0]);
      }

      const jobList = await api.jobs.listWorkspaceJobs(workspaceId);
      setJobs(jobList);
    } catch (err) {
      console.error('Failed to load workspace data', err);
    }
  };

  const handleFetchGithubRepos = async (usernameToFetch?: string) => {
    if (!activeWorkspace) return;
    const user = usernameToFetch || githubUsername;
    if (!user) return;
    setIsFetchingGithub(true);
    try {
      const list = await api.github.listRepos(activeWorkspace.id, user);
      setGithubRepos(list);
    } catch (err: any) {
      alert(err.message || 'Failed to fetch GitHub repositories');
    } finally {
      setIsFetchingGithub(false);
    }
  };

  const handleImportRepo = async (repo: any) => {
    if (!activeWorkspace) return;
    setImportingRepoName(repo.fullName);
    try {
      await api.github.importRepo(activeWorkspace.id, {
        fullName: repo.fullName,
        name: repo.name,
        cloneUrl: repo.cloneUrl,
        defaultBranch: repo.defaultBranch,
      });
      setShowConnectModal(false);
      await loadWorkspaceData(activeWorkspace.id);
    } catch (err: any) {
      alert(err.message || 'Failed to import repository');
    } finally {
      setImportingRepoName(null);
    }
  };

  const handleConnectRepo = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeWorkspace) return;
    try {
      await api.repositories.connect(activeWorkspace.id, {
        name: repoName,
        fullName: repoFullName,
        cloneUrl: cloneUrl,
        defaultBranch: defaultBranch,
      });
      setShowConnectModal(false);
      setRepoName('');
      setRepoFullName('');
      setCloneUrl('');
      await loadWorkspaceData(activeWorkspace.id);
    } catch (err: any) {
      alert(err.message || 'Failed to connect repository');
    }
  };

  const handleTriggerRun = async (repoId: number) => {
    if (!activeWorkspace) return;
    setIsTriggering(true);
    try {
      await api.jobs.trigger(activeWorkspace.id, repoId);
      await loadWorkspaceData(activeWorkspace.id);
    } catch (err: any) {
      alert(err.message || 'Failed to trigger health check');
    } finally {
      setIsTriggering(false);
    }
  };

  const handleOpenJobDetail = async (jobId: number) => {
    if (!activeWorkspace) return;
    try {
      const detail = await api.jobs.getDetail(activeWorkspace.id, jobId);
      setSelectedJobDetail(detail);
      const fix = await api.fix.getLatest(activeWorkspace.id, jobId).catch(() => null);
      setActiveFix(fix);
      setActiveDetailTab(detail.job.status === 'FAILED' ? 'ai' : 'logs');
    } catch (err: any) {
      alert(err.message || 'Failed to load job detail');
    }
  };

  const handleCancelJob = async (jobId: number) => {
    if (!activeWorkspace) return;
    if (!confirm(`Are you sure you want to cancel Job #${jobId}?`)) return;
    try {
      await api.jobs.cancel(activeWorkspace.id, jobId);
      await loadWorkspaceData(activeWorkspace.id);
    } catch (err: any) {
      alert(err.message || 'Failed to cancel job');
    }
  };

  const handleAttemptFix = async () => {
    if (!activeWorkspace || !selectedJobDetail) return;
    setIsFixing(true);
    try {
      const fix = await api.fix.attempt(activeWorkspace.id, selectedJobDetail.job.id);
      setActiveFix(fix);
      setActiveDetailTab('fix');
    } catch (err: any) {
      alert(err.message || 'Fix attempt failed');
    } finally {
      setIsFixing(false);
    }
  };

  const handleOpenPr = async () => {
    if (!activeWorkspace || !selectedJobDetail || !activeFix) return;
    setIsCreatingPr(true);
    try {
      const updated = await api.fix.openPr(activeWorkspace.id, selectedJobDetail.job.id, activeFix.id);
      setActiveFix(updated);
    } catch (err: any) {
      alert(err.message || 'Failed to create PR');
    } finally {
      setIsCreatingPr(false);
    }
  };

  const handleSaveConfig = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeWorkspace || !selectedRepo) return;
    try {
      await api.repositories.updateConfig(activeWorkspace.id, selectedRepo.id, editConfig);
      setShowConfigModal(false);
      await loadWorkspaceData(activeWorkspace.id);
    } catch (err: any) {
      alert(err.message || 'Failed to update configuration');
    }
  };

  const handleCopyMarkdown = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  if (loading) {
    return (
      <div className="flex flex-col h-screen items-center justify-center bg-[#090d16] text-slate-400 gap-3">
        <RotateCw className="h-6 w-6 animate-spin text-emerald-400" />
        <span className="text-sm font-medium text-slate-300">Loading workspace dashboard...</span>
        <button
          onClick={() => { removeToken(); router.push('/login'); }}
          className="mt-3 text-xs text-slate-500 hover:text-emerald-400 transition underline underline-offset-4"
        >
          Taking longer than expected? Return to Login
        </button>
      </div>
    );
  }

  return (
    <div className="relative min-h-screen bg-[#090d16] text-slate-100 flex flex-col overflow-x-hidden">
      {/* Ambient Gradient Background Glows */}
      <div className="fixed top-0 left-1/4 h-[500px] w-[500px] rounded-full bg-emerald-500/10 blur-[140px] pointer-events-none -z-10" />
      <div className="fixed bottom-10 right-1/4 h-[550px] w-[550px] rounded-full bg-cyan-500/10 blur-[150px] pointer-events-none -z-10" />
      <div className="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 h-[600px] w-[600px] rounded-full bg-indigo-500/5 blur-[160px] pointer-events-none -z-10" />

      {/* Top Navigation */}
      <header className="sticky top-0 z-40 border-b border-white/5 bg-slate-900/50 backdrop-blur-xl px-3.5 sm:px-6 py-3 sm:py-3.5 flex items-center justify-between gap-2 sm:gap-4 shadow-sm">
        <div className="flex items-center gap-2.5 sm:gap-3 shrink-0">
          <div className="flex h-8 w-8 sm:h-9 sm:w-9 items-center justify-center rounded-xl bg-gradient-to-br from-emerald-500/20 to-teal-500/10 text-emerald-400 border border-emerald-500/30 shadow-md shadow-emerald-500/10 shrink-0">
            <ShieldCheck className="h-4 w-4 sm:h-5 sm:w-5" />
          </div>
          <div>
            <h1 className="text-sm sm:text-base font-black tracking-tight text-white">
              <span className="bg-gradient-to-r from-emerald-400 via-teal-300 to-cyan-400 bg-clip-text text-transparent">
                AutoMaintain
              </span>
            </h1>
          </div>
        </div>

        <div className="flex items-center gap-2 sm:gap-3">
          <div className="flex items-center gap-1.5 sm:gap-2 text-xs text-slate-400 bg-slate-900/80 px-2.5 sm:px-3 py-1 sm:py-1.5 rounded-xl border border-slate-800 shadow-sm">
            <span className="text-slate-500 hidden md:inline">Workspace:</span>
            <span className="font-semibold text-slate-200 max-w-[90px] sm:max-w-[140px] truncate">{activeWorkspace?.name}</span>
          </div>

          <div className="flex items-center gap-1.5 sm:gap-2">
            <span className="text-xs font-medium text-slate-300 hidden sm:inline">{user?.name}</span>
            <button
              onClick={() => { removeToken(); router.push('/login'); }}
              className="p-2 text-slate-400 hover:text-red-400 hover:bg-slate-800/80 rounded-xl transition duration-200"
              title="Sign Out"
            >
              <LogOut className="h-4 w-4" />
            </button>
          </div>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 p-3.5 sm:p-6 max-w-7xl w-full mx-auto space-y-5 sm:space-y-6">
        {/* Workspace Repositories Section */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <h2 className="text-base sm:text-xl font-bold text-white flex items-center gap-2">
              Monitored Repositories
              <span className="text-xs px-2 py-0.5 rounded-full bg-slate-800 text-slate-300 border border-slate-700/60 font-medium">
                {repositories.length}
              </span>
            </h2>
            <p className="text-[11px] sm:text-xs text-slate-400 mt-0.5">Connected repositories with autonomous health checks and AI self-healing</p>
          </div>
          <button
            onClick={() => setShowConnectModal(true)}
            className="flex items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-emerald-500 via-teal-500 to-emerald-600 hover:from-emerald-400 hover:via-teal-400 hover:to-emerald-500 px-4 py-2.5 text-xs font-semibold text-white shadow-lg shadow-emerald-500/20 hover:shadow-emerald-500/35 transition-all duration-300 transform hover:-translate-y-0.5 active:translate-y-0 w-full sm:w-auto"
          >
            <FolderPlus className="h-4 w-4" />
            Connect Repository
          </button>
        </div>

        {repositories.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-slate-800 bg-slate-900/30 p-12 text-center backdrop-blur-sm">
            <GitBranch className="mx-auto h-10 w-10 text-slate-600 mb-3" />
            <h3 className="text-sm font-semibold text-slate-300">No repositories connected yet</h3>
            <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
              Connect a GitHub repository or local project path to start running automated health checks and AI failure diagnosis.
            </p>
            <button
              onClick={() => setShowConnectModal(true)}
              className="mt-4 rounded-xl bg-slate-800 px-4 py-2 text-xs font-medium text-slate-200 hover:bg-slate-700 transition"
            >
              Connect First Repository
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 sm:gap-5">
            {repositories.map((repo) => (
              <div
                key={repo.id}
                className="group relative rounded-2xl border border-slate-800/80 bg-gradient-to-b from-slate-900/90 via-slate-900/60 to-slate-950/90 p-5 shadow-xl hover:border-emerald-500/40 hover:shadow-2xl hover:shadow-emerald-500/10 transition-all duration-300 backdrop-blur-xl flex flex-col justify-between"
              >
                <div>
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <h3 className="font-bold text-white text-sm truncate group-hover:text-emerald-300 transition duration-200">
                        {repo.name}
                      </h3>
                      <p className="text-xs text-slate-400 truncate mt-0.5">{repo.fullName}</p>
                    </div>
                    <span className="text-[10px] font-mono bg-gradient-to-r from-slate-800 to-slate-850 text-slate-300 px-2 py-0.5 rounded-lg border border-slate-700/60 shrink-0">
                      {repo.configuration?.projectType || 'UNKNOWN'}
                    </span>
                  </div>

                  <div className="mt-4 space-y-2 text-xs text-slate-400">
                    <div className="flex items-center gap-2">
                      <GitBranch className="h-3.5 w-3.5 text-slate-500 shrink-0" />
                      <span>Branch: <code className="text-slate-200 bg-slate-950 px-1.5 py-0.5 rounded border border-slate-800">{repo.defaultBranch}</code></span>
                    </div>
                    <div className="flex items-center gap-2">
                      <Terminal className="h-3.5 w-3.5 text-slate-500 shrink-0" />
                      <span className="truncate">Test: <code className="text-slate-200 bg-slate-950 px-1.5 py-0.5 rounded border border-slate-800 font-mono text-[11px]">{repo.configuration?.testCommand}</code></span>
                    </div>
                    <div className="flex items-center gap-2 pt-1">
                      <span className="relative flex h-2 w-2">
                        <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                        <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
                      </span>
                      <span className="text-[11px] text-emerald-400 font-medium">Auto-Monitoring: Active (Daily @ 02:00 AM)</span>
                    </div>
                  </div>
                </div>

                <div className="mt-5 pt-4 border-t border-slate-800/80 flex items-center gap-2">
                  <button
                    onClick={() => handleTriggerRun(repo.id)}
                    disabled={isTriggering}
                    className="flex-1 flex items-center justify-center gap-1.5 rounded-xl bg-gradient-to-r from-emerald-500/15 via-teal-500/10 to-emerald-500/15 text-emerald-300 border border-emerald-500/30 hover:border-emerald-500/50 hover:bg-emerald-500/25 px-3 py-2 text-xs font-semibold shadow-sm transition-all duration-200 disabled:opacity-50"
                  >
                    <Play className="h-3.5 w-3.5" />
                    Run Health Check
                  </button>
                  <button
                    onClick={() => {
                      setSelectedRepo(repo);
                      setEditConfig({
                        projectType: repo.configuration?.projectType,
                        installCommand: repo.configuration?.installCommand,
                        buildCommand: repo.configuration?.buildCommand,
                        testCommand: repo.configuration?.testCommand,
                        timeoutSeconds: repo.configuration?.timeoutSeconds,
                      });
                      setShowConfigModal(true);
                    }}
                    className="p-2 text-slate-400 hover:text-white hover:bg-slate-800/80 border border-slate-800/80 rounded-xl transition"
                    title="Configure Commands"
                  >
                    <Settings className="h-4 w-4" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Health Check Run History */}
        <div className="space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div>
              <h2 className="text-base sm:text-lg font-bold text-white">Execution History</h2>
              <p className="text-[11px] sm:text-xs text-slate-400">Real-time status, logs, duration, commit SHA, and AI failure reports</p>
            </div>
            <button
              onClick={() => activeWorkspace && loadWorkspaceData(activeWorkspace.id)}
              className="flex items-center justify-center gap-1.5 text-xs text-slate-400 hover:text-slate-200 bg-slate-900 border border-slate-800 px-3 py-1.5 rounded-lg w-full sm:w-auto"
            >
              <RotateCw className="h-3.5 w-3.5" />
              Refresh
            </button>
          </div>

          <div className="overflow-x-auto rounded-2xl border border-slate-800/80 bg-slate-900/40 backdrop-blur-xl shadow-xl">
            <table className="w-full text-left text-xs min-w-[640px]">
              <thead className="bg-slate-900/90 text-slate-400 border-b border-slate-800/80 text-[11px] uppercase tracking-wider font-semibold">
                <tr>
                  <th className="py-3.5 px-4">Job ID</th>
                  <th className="py-3.5 px-4">Repository</th>
                  <th className="py-3.5 px-4">Status</th>
                  <th className="py-3.5 px-4">Branch / Commit</th>
                  <th className="py-3.5 px-4">Duration</th>
                  <th className="py-3.5 px-4">Trigger</th>
                  <th className="py-3.5 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/50 text-slate-300">
                {jobs.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="py-10 text-center text-slate-500">
                      No health check jobs executed yet. Trigger a run on any connected repository.
                    </td>
                  </tr>
                ) : (
                  jobs.map((job) => {
                    const isPassed = job.status === 'PASSED';
                    const isFailed = job.status === 'FAILED';
                    const isRunning = job.status === 'RUNNING';
                    const isQueued = job.status === 'QUEUED';

                    return (
                      <tr key={job.id} className="hover:bg-slate-800/30 transition-colors duration-150">
                        <td className="py-3.5 px-4 font-mono font-medium text-slate-200">#{job.id}</td>
                        <td className="py-3.5 px-4 font-medium text-white">{job.repositoryName}</td>
                        <td className="py-3.5 px-4">
                          <span
                            className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold border shadow-sm ${
                              isPassed
                                ? 'bg-gradient-to-r from-emerald-500/15 to-teal-500/10 text-emerald-300 border-emerald-500/30 shadow-emerald-500/10'
                                : isFailed
                                ? 'bg-gradient-to-r from-rose-500/15 to-red-500/10 text-rose-300 border-rose-500/30 shadow-rose-500/10'
                                : isRunning
                                ? 'bg-gradient-to-r from-blue-500/15 to-cyan-500/10 text-blue-300 border-blue-500/30 animate-pulse'
                                : 'bg-gradient-to-r from-amber-500/15 to-yellow-500/10 text-amber-300 border-amber-500/30'
                            }`}
                          >
                            {isPassed && <CheckCircle2 className="h-3.5 w-3.5" />}
                            {isFailed && <XCircle className="h-3.5 w-3.5" />}
                            {isRunning && <RotateCw className="h-3.5 w-3.5 animate-spin" />}
                            {job.status}
                          </span>
                        </td>
                        <td className="py-3.5 px-4 font-mono text-[11px] text-slate-400">
                          {job.branch} • <span className="text-slate-300">{job.commitSha ? job.commitSha.substring(0, 7) : 'HEAD'}</span>
                        </td>
                        <td className="py-3.5 px-4 text-slate-400">
                          {job.durationMs ? `${(job.durationMs / 1000).toFixed(1)}s` : '-'}
                        </td>
                        <td className="py-3.5 px-4 text-slate-400">{job.triggerType}</td>
                        <td className="py-3.5 px-4 text-right">
                          <div className="flex items-center justify-end gap-2">
                            {(isRunning || isQueued) && (
                              <button
                                onClick={() => handleCancelJob(job.id)}
                                className="inline-flex items-center gap-1 rounded-xl bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 px-2.5 py-1.5 text-xs font-semibold border border-rose-500/30 transition duration-150"
                                title="Cancel Running Job"
                              >
                                <XCircle className="h-3.5 w-3.5" />
                                Cancel
                              </button>
                            )}
                            <button
                              onClick={() => handleOpenJobDetail(job.id)}
                              className="inline-flex items-center gap-1.5 rounded-xl bg-gradient-to-r from-slate-800 to-slate-800/80 hover:from-slate-700 hover:to-slate-750 text-slate-200 hover:text-white px-3 py-1.5 text-xs font-semibold border border-slate-700/60 shadow-sm transition duration-150"
                            >
                              <FileText className="h-3.5 w-3.5" />
                              View Run
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>
      </main>

      {/* Connect Repo Modal */}
      {showConnectModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-3 sm:p-4">
          <div className="w-full max-w-xl rounded-2xl border border-slate-800 bg-slate-900 p-4 sm:p-6 shadow-2xl flex flex-col max-h-[90vh] sm:max-h-[85vh]">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div className="flex items-center gap-2">
                <GitBranch className="h-5 w-5 text-emerald-400" />
                <h3 className="text-sm sm:text-base font-bold text-white">Connect GitHub Repository</h3>
              </div>
              <button
                onClick={() => setShowConnectModal(false)}
                className="text-slate-400 hover:text-white rounded-lg p-1"
              >
                ✕
              </button>
            </div>

            {/* Tab Switcher */}
            <div className="flex flex-col sm:flex-row gap-1 sm:gap-2 mt-3 sm:mt-4 bg-slate-950 p-1 rounded-lg border border-slate-800 text-xs">
              <button
                type="button"
                onClick={() => setConnectTab('github')}
                className={`flex-1 py-1.5 rounded-md font-medium transition text-center ${
                  connectTab === 'github' ? 'bg-slate-800 text-white' : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                Link GitHub Account (Autonomous)
              </button>
              <button
                type="button"
                onClick={() => setConnectTab('manual')}
                className={`flex-1 py-1.5 rounded-md font-medium transition text-center ${
                  connectTab === 'manual' ? 'bg-slate-800 text-white' : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                Custom Git URL / Local Path
              </button>
            </div>

            {connectTab === 'github' ? (
              <div className="flex-1 overflow-hidden flex flex-col mt-4">
                <div className="flex flex-col sm:flex-row gap-2 mb-3">
                  <div className="flex-1 relative">
                    <input
                      type="text"
                      value={githubUsername}
                      onChange={(e) => setGithubUsername(e.target.value)}
                      placeholder="GitHub Username (e.g. Nit3710)"
                      className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-xs text-slate-100 placeholder-slate-500 focus:border-emerald-500 focus:outline-none"
                    />
                  </div>
                  <button
                    type="button"
                    onClick={() => handleFetchGithubRepos()}
                    disabled={isFetchingGithub}
                    className="flex items-center justify-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-xs font-semibold text-white hover:bg-emerald-500 disabled:opacity-50 w-full sm:w-auto"
                  >
                    {isFetchingGithub ? <RotateCw className="h-3.5 w-3.5 animate-spin" /> : null}
                    {isFetchingGithub ? 'Loading...' : 'Fetch Repositories'}
                  </button>
                </div>

                <div className="flex-1 overflow-y-auto space-y-2 pr-1 min-h-[220px]">
                  {githubRepos.length === 0 ? (
                    <div className="text-center py-10 text-slate-500 text-xs border border-dashed border-slate-800 rounded-xl">
                      Click <strong>"Fetch Repositories"</strong> above to load all public/authorized repos for <code>{githubUsername || 'your account'}</code>.
                    </div>
                  ) : (
                    githubRepos.map((repo) => (
                      <div
                        key={repo.fullName}
                        className="rounded-xl border border-slate-800 bg-slate-950/70 p-3 sm:p-3.5 flex flex-col sm:flex-row sm:items-center justify-between gap-2.5 hover:border-slate-700 transition"
                      >
                        <div className="min-w-0 pr-0 sm:pr-3">
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="font-semibold text-white text-xs truncate max-w-[200px] sm:max-w-none">{repo.fullName}</span>
                            {repo.language && (
                              <span className="text-[10px] font-mono bg-slate-800 text-slate-300 px-1.5 py-0.5 rounded border border-slate-700">
                                {repo.language}
                              </span>
                            )}
                          </div>
                          {repo.description && (
                            <p className="text-[11px] text-slate-400 truncate mt-0.5 max-w-sm">{repo.description}</p>
                          )}
                        </div>

                        <button
                          type="button"
                          disabled={repo.alreadyConnected || importingRepoName === repo.fullName}
                          onClick={() => handleImportRepo(repo)}
                          className={`rounded-lg px-3 py-1.5 text-xs font-medium transition w-full sm:w-auto text-center shrink-0 ${
                            repo.alreadyConnected
                              ? 'bg-slate-800 text-slate-500 cursor-not-allowed'
                              : 'bg-emerald-600 hover:bg-emerald-500 text-white shadow'
                          }`}
                        >
                          {importingRepoName === repo.fullName
                            ? 'Detecting & Importing...'
                            : repo.alreadyConnected
                            ? 'Connected'
                            : '⚡ Import & Monitor'}
                        </button>
                      </div>
                    ))
                  )}
                </div>
                <p className="text-[11px] text-slate-500 mt-3 text-center">
                  ⚡ <strong>100% Zero-Touch:</strong> Importing will automatically inspect files, configure tests, and trigger Run #1.
                </p>
              </div>
            ) : (
              <form onSubmit={handleConnectRepo} className="space-y-3.5 mt-4">
                <div>
                  <label className="block text-xs font-medium text-slate-300">Display Name</label>
                  <input
                    type="text"
                    required
                    value={repoName}
                    onChange={(e) => setRepoName(e.target.value)}
                    placeholder="e.g. backend-api"
                    className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-xs text-slate-100 focus:border-emerald-500 focus:outline-none"
                  />
                </div>

                <div>
                  <label className="block text-xs font-medium text-slate-300">Full Name (owner/repo)</label>
                  <input
                    type="text"
                    required
                    value={repoFullName}
                    onChange={(e) => setRepoFullName(e.target.value)}
                    placeholder="e.g. acme-org/backend-api"
                    className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-xs text-slate-100 focus:border-emerald-500 focus:outline-none"
                  />
                </div>

                <div>
                  <label className="block text-xs font-medium text-slate-300">Clone URL / Local Directory Path</label>
                  <input
                    type="text"
                    required
                    value={cloneUrl}
                    onChange={(e) => setCloneUrl(e.target.value)}
                    placeholder="https://github.com/org/repo.git or C:\projects\my-bot"
                    className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-xs text-slate-100 focus:border-emerald-500 focus:outline-none"
                  />
                </div>

                <div className="mt-5 flex gap-2 justify-end">
                  <button
                    type="button"
                    onClick={() => setShowConnectModal(false)}
                    className="rounded-lg bg-slate-800 px-3.5 py-2 text-xs font-medium text-slate-300 hover:bg-slate-700"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="rounded-lg bg-emerald-600 px-4 py-2 text-xs font-semibold text-white hover:bg-emerald-500"
                  >
                    Connect
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}

      {/* Configure Commands Modal */}
      {showConfigModal && selectedRepo && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-3 sm:p-4">
          <div className="w-full max-w-lg rounded-2xl border border-slate-800 bg-slate-900 p-4 sm:p-6 shadow-2xl max-h-[90vh] overflow-y-auto">
            <h3 className="text-sm sm:text-base font-bold text-white mb-1">Configure Health Check Commands</h3>
            <p className="text-[11px] sm:text-xs text-slate-400 mb-4 truncate">{selectedRepo.fullName}</p>

            <form onSubmit={handleSaveConfig} className="space-y-3.5">
              <div>
                <label className="block text-xs font-medium text-slate-300">Project Type</label>
                <select
                  value={editConfig.projectType || 'PYTHON'}
                  onChange={(e) => setEditConfig({ ...editConfig, projectType: e.target.value })}
                  className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-xs text-slate-100 focus:border-emerald-500 focus:outline-none"
                >
                  <option value="PYTHON">Python (pip / pytest / unittest)</option>
                  <option value="JAVA_MAVEN">Java (Maven) - Java 17 / Spring Boot</option>
                  <option value="JAVA_GRADLE">Java (Gradle)</option>
                  <option value="NEXT_JS">Next.js (React / npm)</option>
                  <option value="REACT_NPM">React (npm)</option>
                  <option value="GENERIC_NODE">Node.js</option>
                  <option value="CUSTOM">Custom Command</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-300">Build Command</label>
                <input
                  type="text"
                  value={editConfig.buildCommand || ''}
                  onChange={(e) => setEditConfig({ ...editConfig, buildCommand: e.target.value })}
                  placeholder="e.g. mvn clean compile"
                  className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-xs text-slate-100 focus:border-emerald-500 focus:outline-none font-mono"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-300">Test Command</label>
                <input
                  type="text"
                  value={editConfig.testCommand || ''}
                  onChange={(e) => setEditConfig({ ...editConfig, testCommand: e.target.value })}
                  placeholder="e.g. mvn test"
                  className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-xs text-slate-100 focus:border-emerald-500 focus:outline-none font-mono"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-300">Execution Timeout (seconds)</label>
                <input
                  type="number"
                  value={editConfig.timeoutSeconds || 300}
                  onChange={(e) => setEditConfig({ ...editConfig, timeoutSeconds: parseInt(e.target.value) })}
                  className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-xs text-slate-100 focus:border-emerald-500 focus:outline-none"
                />
              </div>

              <div className="mt-5 flex flex-col-reverse sm:flex-row gap-2 justify-end">
                <button
                  type="button"
                  onClick={() => setShowConfigModal(false)}
                  className="rounded-lg bg-slate-800 px-3.5 py-2 text-xs font-medium text-slate-300 hover:bg-slate-700 w-full sm:w-auto text-center"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="rounded-lg bg-emerald-600 px-4 py-2 text-xs font-semibold text-white hover:bg-emerald-500 w-full sm:w-auto text-center"
                >
                  Save Configuration
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Detailed Job Modal */}
      {selectedJobDetail && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-2 sm:p-4">
          <div className="w-full max-w-4xl h-[94vh] sm:h-[85vh] rounded-2xl border border-slate-800 bg-slate-900 shadow-2xl flex flex-col overflow-hidden">
            {/* Modal Header */}
            <div className="px-3.5 sm:px-6 py-3 sm:py-4 border-b border-slate-800 flex flex-col md:flex-row md:items-center justify-between gap-3 bg-slate-900/90">
              <div className="flex items-center justify-between">
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="font-bold text-white text-sm sm:text-base">Run #{selectedJobDetail.job.id}</h3>
                    <span
                      className={`text-[10px] sm:text-[11px] font-semibold px-2 py-0.5 rounded-full border ${
                        selectedJobDetail.job.status === 'PASSED'
                          ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
                          : 'bg-rose-500/10 text-rose-400 border-rose-500/20'
                      }`}
                    >
                      {selectedJobDetail.job.status}
                    </span>
                  </div>
                  <p className="text-[11px] sm:text-xs text-slate-400 mt-0.5 max-w-[250px] sm:max-w-none truncate">
                    {selectedJobDetail.job.repositoryName} • {selectedJobDetail.job.branch} • SHA:{' '}
                    {selectedJobDetail.job.commitSha ? selectedJobDetail.job.commitSha.substring(0, 8) : 'N/A'}
                  </p>
                </div>
                {/* Mobile close button */}
                <button
                  onClick={() => setSelectedJobDetail(null)}
                  className="md:hidden rounded-lg p-1.5 text-slate-400 hover:text-white hover:bg-slate-800"
                >
                  ✕
                </button>
              </div>

              <div className="flex items-center gap-2 overflow-x-auto pb-1 md:pb-0">
                <div className="flex rounded-lg bg-slate-950 p-1 border border-slate-800 text-xs shrink-0 overflow-x-auto">
                  <button
                    onClick={() => setActiveDetailTab('logs')}
                    className={`px-2.5 sm:px-3 py-1 rounded-md font-medium transition flex items-center gap-1.5 whitespace-nowrap text-[11px] sm:text-xs ${
                      activeDetailTab === 'logs' ? 'bg-slate-800 text-white' : 'text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    <Terminal className="h-3.5 w-3.5" />
                    Step Logs
                  </button>
                  <button
                    onClick={() => setActiveDetailTab('ai')}
                    className={`px-2.5 sm:px-3 py-1 rounded-md font-medium transition flex items-center gap-1.5 whitespace-nowrap text-[11px] sm:text-xs ${
                      activeDetailTab === 'ai' ? 'bg-slate-800 text-emerald-400' : 'text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    <Bot className="h-3.5 w-3.5" />
                    AI Diagnosis
                  </button>
                  <button
                    onClick={() => setActiveDetailTab('fix')}
                    className={`px-2.5 sm:px-3 py-1 rounded-md font-medium transition flex items-center gap-1.5 whitespace-nowrap text-[11px] sm:text-xs ${
                      activeDetailTab === 'fix' ? 'bg-slate-800 text-amber-400' : 'text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    <ShieldCheck className="h-3.5 w-3.5" />
                    Verified Fix & PR
                  </button>
                  <button
                    onClick={() => setActiveDetailTab('report')}
                    className={`px-2.5 sm:px-3 py-1 rounded-md font-medium transition flex items-center gap-1.5 whitespace-nowrap text-[11px] sm:text-xs ${
                      activeDetailTab === 'report' ? 'bg-slate-800 text-white' : 'text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    <FileText className="h-3.5 w-3.5" />
                    Markdown Report
                  </button>
                </div>
                {/* Desktop close button */}
                <button
                  onClick={() => setSelectedJobDetail(null)}
                  className="hidden md:block rounded-lg p-1.5 text-slate-400 hover:text-white hover:bg-slate-800 shrink-0"
                >
                  ✕
                </button>
              </div>
            </div>

            {/* Modal Body */}
            <div className="flex-1 overflow-y-auto p-3.5 sm:p-6 bg-slate-950/50">
              {activeDetailTab === 'logs' && (
                <div className="space-y-4">
                  {selectedJobDetail.logs.map((log: any) => (
                    <div key={log.id} className="rounded-xl border border-slate-800 bg-slate-900/90 overflow-hidden">
                      <div className="px-4 py-2.5 bg-slate-900 border-b border-slate-800 flex items-center justify-between text-xs">
                        <div className="flex items-center gap-2">
                          <span className="font-semibold text-white">Step {log.stepOrder}: {log.stepName}</span>
                          <span
                            className={`px-2 py-0.5 rounded text-[10px] font-medium ${
                              log.exitCode === 0
                                ? 'bg-emerald-500/10 text-emerald-400'
                                : 'bg-rose-500/10 text-rose-400'
                            }`}
                          >
                            Exit Code: {log.exitCode}
                          </span>
                        </div>
                        <span className="text-slate-400">{log.durationMs}ms</span>
                      </div>
                      <pre className="p-4 text-xs font-mono text-slate-300 overflow-x-auto max-h-60 leading-relaxed">
                        {log.output || '(No output recorded)'}
                      </pre>
                    </div>
                  ))}
                </div>
              )}

              {activeDetailTab === 'ai' && (
                <div>
                  {selectedJobDetail.aiAnalysis ? (
                    <div className="space-y-4">
                      <div className="rounded-xl border border-emerald-500/30 bg-emerald-950/20 p-4">
                        <div className="flex items-center gap-2 text-emerald-400 font-semibold text-sm mb-1">
                          <Bot className="h-4 w-4" />
                          Failure Summary
                        </div>
                        <p className="text-xs text-slate-200 leading-relaxed">
                          {selectedJobDetail.aiAnalysis.failureSummary}
                        </p>
                      </div>

                      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div className="rounded-xl border border-slate-800 bg-slate-900/80 p-4">
                          <h4 className="text-xs font-semibold text-white mb-2">🔍 Likely Root Cause</h4>
                          <p className="text-xs text-slate-300 leading-relaxed">
                            {selectedJobDetail.aiAnalysis.likelyRootCause}
                          </p>
                        </div>

                        <div className="rounded-xl border border-slate-800 bg-slate-900/80 p-4">
                          <h4 className="text-xs font-semibold text-white mb-2">💡 Recommended Remediation</h4>
                          <p className="text-xs text-slate-300 leading-relaxed">
                            {selectedJobDetail.aiAnalysis.suggestedSolution}
                          </p>
                        </div>
                      </div>

                      {selectedJobDetail.aiAnalysis.confirmedFacts && (
                        <div className="rounded-xl border border-slate-800 bg-slate-900/80 p-4">
                          <h4 className="text-xs font-semibold text-white mb-2">📋 Confirmed Facts vs Hypotheses</h4>
                          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                            <div>
                              <span className="text-slate-400 block mb-1">Confirmed Facts:</span>
                              <pre className="text-slate-200 whitespace-pre-wrap font-sans bg-slate-950 p-2 rounded border border-slate-800">
                                {selectedJobDetail.aiAnalysis.confirmedFacts}
                              </pre>
                            </div>
                            <div>
                              <span className="text-slate-400 block mb-1">Hypotheses:</span>
                              <pre className="text-slate-200 whitespace-pre-wrap font-sans bg-slate-950 p-2 rounded border border-slate-800">
                                {selectedJobDetail.aiAnalysis.hypotheses}
                              </pre>
                            </div>
                          </div>
                        </div>
                      )}

                      <div className="rounded-xl border border-slate-800 bg-slate-900/80 p-4">
                        <h4 className="text-xs font-semibold text-white mb-2">🧪 What to Test Next</h4>
                        <p className="text-xs text-slate-300 font-mono bg-slate-950 p-2.5 rounded border border-slate-800">
                          {selectedJobDetail.aiAnalysis.nextStepsToTest}
                        </p>
                      </div>
                    </div>
                  ) : (
                    <div className="text-center py-12 text-slate-500 text-xs">
                      {selectedJobDetail.job.status === 'PASSED'
                        ? 'Health check passed with zero failures. AI failure diagnosis is only required when checks fail.'
                        : 'No AI analysis generated for this run yet.'}
                    </div>
                  )}
                </div>
              )}

              {activeDetailTab === 'fix' && (
                <div className="space-y-4">
                  {!activeFix ? (
                    selectedJobDetail.job.status === 'PASSED' ? (
                      <div className="rounded-xl border border-emerald-500/30 bg-emerald-950/10 p-8 text-center">
                        <CheckCircle2 className="mx-auto h-12 w-12 text-emerald-400 mb-3" />
                        <h4 className="text-sm font-bold text-white">All Automated Checks Passed!</h4>
                        <p className="text-xs text-slate-300 mt-1 max-w-md mx-auto">
                          Repository build and test suites succeeded with exit code 0. No bug remediation or code patches are required for this healthy run.
                        </p>
                      </div>
                    ) : (
                      <div className="rounded-xl border border-amber-500/30 bg-amber-950/10 p-6 text-center">
                        <ShieldCheck className="mx-auto h-10 w-10 text-amber-400 mb-3" />
                        <h4 className="text-sm font-bold text-white">Autonomous Bug Remediation & Branch Verification</h4>
                        <p className="text-xs text-slate-400 mt-1 max-w-lg mx-auto">
                          AI will create an isolated git branch (<code>fix/autofix-run-{selectedJobDetail.job.id}</code>), apply a targeted code patch, re-run all test commands in a sandbox, and verify the fix without ever touching your main branch.
                        </p>
                        <button
                          onClick={handleAttemptFix}
                          disabled={isFixing}
                          className="mt-5 inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-5 py-2.5 text-xs font-semibold text-white shadow-lg hover:bg-emerald-500 transition disabled:opacity-50"
                        >
                          {isFixing ? <RotateCw className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}
                          {isFixing ? 'Testing Patch in Isolated Sandbox...' : '⚡ Generate & Verify Fix Now'}
                        </button>
                      </div>
                    )
                  ) : (
                    <div className="space-y-4">
                      {/* Verification Status Banner */}
                      <div
                        className={`rounded-xl border p-3.5 sm:p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3.5 ${
                          activeFix.verificationStatus === 'VERIFIED_PASSED'
                            ? 'border-emerald-500/30 bg-emerald-950/20 text-emerald-400'
                            : 'border-rose-500/30 bg-rose-950/20 text-rose-400'
                        }`}
                      >
                        <div className="flex items-start sm:items-center gap-3">
                          {activeFix.verificationStatus === 'VERIFIED_PASSED' ? (
                            <CheckCircle2 className="h-5 w-5 sm:h-6 sm:w-6 text-emerald-400 shrink-0 mt-0.5 sm:mt-0" />
                          ) : (
                            <XCircle className="h-5 w-5 sm:h-6 sm:w-6 text-rose-400 shrink-0 mt-0.5 sm:mt-0" />
                          )}
                          <div>
                            <h4 className="font-bold text-xs sm:text-sm text-white">
                              {activeFix.verificationStatus === 'VERIFIED_PASSED'
                                ? 'Fix Verified: All Automated Checks Passed!'
                                : 'Fix Attempted: Tests Still Failing'}
                            </h4>
                            <p className="text-[11px] sm:text-xs text-slate-300 mt-0.5">
                              Branch: <code className="text-white font-mono">{activeFix.branchName}</code> • Target File:{' '}
                              <code className="text-white font-mono">{activeFix.targetFilePath}</code>
                            </p>
                          </div>
                        </div>

                        {/* Open PR Action */}
                        <div className="w-full sm:w-auto">
                          {!activeFix.pullRequestUrl ? (
                            <button
                              onClick={handleOpenPr}
                              disabled={isCreatingPr}
                              className="flex items-center justify-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-xs font-semibold text-white shadow hover:bg-emerald-500 transition disabled:opacity-50 w-full sm:w-auto"
                            >
                              {isCreatingPr ? <RotateCw className="h-3.5 w-3.5 animate-spin" /> : <GitBranch className="h-3.5 w-3.5" />}
                              {isCreatingPr ? 'Opening PR...' : '🚀 Open Pull Request'}
                            </button>
                          ) : (
                            <a
                              href={activeFix.pullRequestUrl}
                              target="_blank"
                              rel="noreferrer"
                              className="inline-flex items-center justify-center gap-1.5 rounded-lg bg-purple-600 px-4 py-2 text-xs font-semibold text-white shadow hover:bg-purple-500 transition w-full sm:w-auto"
                            >
                              <GitBranch className="h-3.5 w-3.5" />
                              {activeFix.pullRequestNumber && activeFix.pullRequestNumber !== 'New'
                                ? `View PR #${activeFix.pullRequestNumber} on GitHub`
                                : 'View GitHub Pull Requests ↗'}
                            </a>
                          )}
                        </div>
                      </div>

                      {/* Unified Git Diff Viewer */}
                      <div className="rounded-xl border border-slate-800 bg-slate-900/90 overflow-hidden">
                        <div className="px-4 py-2.5 bg-slate-900 border-b border-slate-800 flex items-center justify-between text-xs">
                          <span className="font-semibold text-white">Verified Code Patch (Git Diff)</span>
                          <div className="flex items-center gap-3">
                            <button
                              onClick={() => handleCopyMarkdown(activeFix.diff)}
                              className="text-xs text-emerald-400 hover:text-emerald-300 flex items-center gap-1 font-sans"
                            >
                              <Copy className="h-3.5 w-3.5" />
                              {copied ? 'Copied Diff!' : 'Copy Patch'}
                            </button>
                            <span className="text-slate-400 text-[11px] font-mono">{activeFix.targetFilePath}</span>
                          </div>
                        </div>
                        <pre className="p-4 text-xs font-mono overflow-x-auto max-h-64 leading-relaxed bg-slate-950 text-slate-300">
                          {activeFix.diff && !activeFix.diff.includes('fatal:') ? (
                            activeFix.diff.split('\n').map((line: string, i: number) => {
                              if (line.startsWith('+')) {
                                return <span key={i} className="text-emerald-400 block bg-emerald-950/30 px-1">{line}</span>;
                              }
                              if (line.startsWith('-')) {
                                return <span key={i} className="text-rose-400 block bg-rose-950/30 px-1">{line}</span>;
                              }
                              return <span key={i} className="text-slate-400 block">{line}</span>;
                            })
                          ) : (
                            <span className="text-emerald-400 block">
                              # All health checks and tests passed cleanly in this run.{'\n'}
                              # Repository is healthy. No code modifications or bug fixes required.
                            </span>
                          )}
                        </pre>
                      </div>

                      {/* Verification Test Output */}
                      {activeFix.testOutput && (
                        <div className="rounded-xl border border-slate-800 bg-slate-900/90 overflow-hidden">
                          <div className="px-4 py-2 bg-slate-900 border-b border-slate-800 text-xs font-semibold text-white">
                            Post-Patch Verification Test Output (Exit Code: {activeFix.testExitCode})
                          </div>
                          <pre className="p-4 text-xs font-mono text-slate-300 overflow-x-auto max-h-40 leading-relaxed bg-slate-950">
                            {activeFix.testOutput}
                          </pre>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}

              {activeDetailTab === 'report' && (
                <div className="space-y-4">
                  <div className="flex justify-between items-center">
                    <span className="text-xs text-slate-400">Downloadable / copyable Markdown report (FR-09)</span>
                    <button
                      onClick={() => handleCopyMarkdown(selectedJobDetail.report?.markdownContent || '')}
                      className="flex items-center gap-1.5 rounded-lg bg-slate-800 px-3 py-1.5 text-xs text-slate-200 hover:bg-slate-700 transition"
                    >
                      {copied ? <Check className="h-3.5 w-3.5 text-emerald-400" /> : <Copy className="h-3.5 w-3.5" />}
                      {copied ? 'Copied!' : 'Copy Markdown'}
                    </button>
                  </div>
                  <pre className="rounded-xl border border-slate-800 bg-slate-950 p-5 text-xs font-mono text-slate-300 overflow-x-auto leading-relaxed whitespace-pre-wrap">
                    {selectedJobDetail.report?.markdownContent || 'Report generating...'}
                  </pre>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
