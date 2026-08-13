
import { useState, useEffect } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Github, ExternalLink, Calendar, Star, GitFork, Code } from 'lucide-react';
import { Skeleton } from '@/components/ui/skeleton';

interface Repository {
  name: string;
  description: string;
  html_url: string;
  stargazers_count: number;
  forks_count: number;
  language: string;
  updated_at: string;
}

const GitHubWidget = () => {
  const username = "gurdipscode";
  const [repos, setRepos] = useState<Repository[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchGitHubData = async () => {
      try {
        setLoading(true);
        const response = await fetch(`https://api.github.com/users/${username}/repos?sort=updated&per_page=4`);
        
        if (!response.ok) {
          throw new Error(`GitHub API returned ${response.status}`);
        }
        
        const data = await response.json();
        setRepos(data);
        setLoading(false);
      } catch (err) {
        console.error('Error fetching GitHub data:', err);
        setError('Failed to load GitHub activity');
        setLoading(false);
      }
    };

    fetchGitHubData();
  }, []);

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  };

  if (error) {
    return (
      <section id="github" className="py-10 bg-[#0a0f1f] text-white">
        <div className="container">
          <div className="flex items-center gap-2 mb-6">
            <Github className="h-6 w-6 text-devops-accent" />
            <h2 className="text-2xl font-bold">GitHub Activity</h2>
          </div>
          <Card className="bg-[#1a1f2e] border-gray-800 text-white">
            <CardContent className="p-6">
              <div className="text-center py-8">
                <p>{error}</p>
              </div>
            </CardContent>
          </Card>
        </div>
      </section>
    );
  }

  return (
    <section id="github" className="py-10 bg-[#0a0f1f] text-white">
      <div className="container">
        <div className="flex items-center gap-2 mb-6">
          <Github className="h-6 w-6 text-devops-accent" />
          <h2 className="text-2xl font-bold">GitHub Activity</h2>
        </div>
        
        <Card className="bg-[#1a1f2e] border-gray-800 text-white">
          <CardContent className="p-6">
            <div className="flex items-center justify-between mb-6">
              <div className="flex items-center gap-2">
                <Github className="h-5 w-5 text-devops-accent" />
                <h3 className="text-lg font-semibold">Recent Repositories</h3>
              </div>
              <a 
                href={`https://github.com/${username}`} 
                target="_blank" 
                rel="noopener noreferrer"
                className="text-sm flex items-center gap-1 text-devops-accent hover:underline"
              >
                View Profile <ExternalLink className="h-3.5 w-3.5" />
              </a>
            </div>
            
            {loading ? (
              <div className="grid gap-4">
                {[...Array(4)].map((_, i) => (
                  <div key={i} className="border border-gray-800 rounded-lg p-4">
                    <Skeleton className="h-6 w-3/4 mb-2 bg-gray-800" />
                    <Skeleton className="h-4 w-full mb-3 bg-gray-800" />
                    <div className="flex items-center justify-between mt-2">
                      <Skeleton className="h-4 w-1/4 bg-gray-800" />
                      <div className="flex items-center gap-3">
                        <Skeleton className="h-4 w-12 bg-gray-800" />
                        <Skeleton className="h-4 w-12 bg-gray-800" />
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <>
                {repos.length > 0 ? (
                  <div className="grid gap-4">
                    {repos.map((repo) => (
                      <a 
                        key={repo.name}
                        href={repo.html_url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="border border-gray-800 rounded-lg p-4 hover:border-devops-accent/50 transition-colors"
                      >
                        <h4 className="font-medium text-devops-accent">{repo.name}</h4>
                        <p className="text-sm text-gray-400 my-1 line-clamp-2">
                          {repo.description || "No description provided"}
                        </p>
                        
                        <div className="flex items-center justify-between mt-2 text-xs text-gray-400">
                          <div className="flex items-center gap-1">
                            <Calendar className="h-3.5 w-3.5" />
                            <span>Updated {formatDate(repo.updated_at)}</span>
                          </div>
                          
                          <div className="flex items-center gap-3">
                            <div className="flex items-center gap-1">
                              <Star className="h-3.5 w-3.5" />
                              <span>{repo.stargazers_count}</span>
                            </div>
                            
                            <div className="flex items-center gap-1">
                              <GitFork className="h-3.5 w-3.5" />
                              <span>{repo.forks_count}</span>
                            </div>
                            
                            {repo.language && (
                              <div className="flex items-center gap-1">
                                <Code className="h-3.5 w-3.5" />
                                <span>{repo.language}</span>
                              </div>
                            )}
                          </div>
                        </div>
                      </a>
                    ))}
                  </div>
                ) : (
                  <div className="text-center py-4">
                    <p>No public repositories found</p>
                  </div>
                )}
                
                <div className="mt-6 text-center">
                  <a
                    href={`https://github.com/${username}?tab=repositories`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 px-4 py-2 bg-[#2a2f3e] hover:bg-[#3a3f4e] rounded-md text-sm transition-colors"
                  >
                    View All Repositories <ExternalLink className="h-3.5 w-3.5" />
                  </a>
                </div>
              </>
            )}
          </CardContent>
        </Card>
      </div>
    </section>
  );
};

export default GitHubWidget;
