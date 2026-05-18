import { Link } from 'react-router';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { LayoutGrid, CheckCircle2, Users, Zap, Shield, BarChart3, ArrowRight } from 'lucide-react';
import { ImageWithFallback } from '../components/figma/ImageWithFallback';

export function Landing() {
  const features = [
    {
      icon: LayoutGrid,
      title: 'Kanban Boards',
      description: 'Visualize your workflow with intuitive drag-and-drop task management',
    },
    {
      icon: Users,
      title: 'Team Collaboration',
      description: 'Work together seamlessly with real-time updates and notifications',
    },
    {
      icon: Zap,
      title: 'Sprint Planning',
      description: 'Plan and execute sprints with built-in velocity tracking and burndown charts',
    },
    {
      icon: Shield,
      title: 'Secure & Reliable',
      description: 'Enterprise-grade security with data encryption and regular backups',
    },
    {
      icon: BarChart3,
      title: 'Advanced Analytics',
      description: 'Gain insights with comprehensive reports and performance metrics',
    },
    {
      icon: CheckCircle2,
      title: 'Task Management',
      description: 'Create, assign, and track tasks with priorities, tags, and custom fields',
    },
  ];

  return (
    <div className="min-h-screen bg-white dark:bg-gray-950">
      {/* Navigation */}
      <nav className="border-b border-gray-200 dark:border-gray-800">
        <div className="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 bg-[#30c2b7] rounded-lg flex items-center justify-center">
              <LayoutGrid className="w-5 h-5 text-white" />
            </div>
            <span className="font-semibold text-xl dark:text-white">Just To Do It</span>
          </div>
          
          <div className="flex items-center gap-4">
            <Link to="/login">
              <Button variant="ghost">Sign in</Button>
            </Link>
            <Link to="/register">
              <Button>Get Started</Button>
            </Link>
          </div>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="py-20 px-6">
        <div className="max-w-7xl mx-auto">
          <div className="grid lg:grid-cols-2 gap-12 items-center">
            <div>
              <h1 className="text-5xl lg:text-6xl mb-6 dark:text-white">
                Project management made <span className="text-[#30c2b7]">simple</span>
              </h1>
              <p className="text-xl text-gray-600 dark:text-gray-400 mb-8">
                Just to do It helps teams plan, track, and ship better software. 
                From sprints to releases, manage everything in one place.
              </p>
              <div className="flex flex-wrap gap-4">
                <Link to="/register">
                  <Button size="lg" className="text-lg">
                    Start Free Trial
                    <ArrowRight className="w-5 h-5 ml-2" />
                  </Button>
                </Link>
                <Link to="/login">
                  <Button size="lg" variant="outline" className="text-lg">
                    View Demo
                  </Button>
                </Link>
              </div>
              <p className="text-sm text-gray-500 dark:text-gray-500 mt-4">
                No credit card required • Free 14-day trial • Cancel anytime
              </p>
            </div>
            
            <div className="relative">
              <div className="rounded-xl overflow-hidden shadow-2xl border border-gray-200 dark:border-gray-800">
                <ImageWithFallback
                  src="https://images.unsplash.com/photo-1758873268663-5a362616b5a7?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHx0ZWFtJTIwY29sbGFib3JhdGlvbiUyMHdvcmtzcGFjZSUyMG1vZGVybiUyMG9mZmljZXxlbnwxfHx8fDE3NzMwODExNzZ8MA&ixlib=rb-4.1.0&q=80&w=1080"
                  alt="Team collaboration"
                  className="w-full h-auto"
                />
              </div>
              <div className="absolute -bottom-6 -left-6 w-32 h-32 bg-indigo-600 rounded-full opacity-20 blur-3xl"></div>
              <div className="absolute -top-6 -right-6 w-32 h-32 bg-purple-600 rounded-full opacity-20 blur-3xl"></div>
            </div>
          </div>
        </div>
      </section>

      {/* Features Section 
      <section className="py-20 px-6 bg-gray-50 dark:bg-gray-900">
        <div className="max-w-7xl mx-auto">
          <div className="text-center mb-16">
            <h2 className="text-4xl mb-4 dark:text-white">Everything you need to ship faster</h2>
            <p className="text-xl text-gray-600 dark:text-gray-400">
              Powerful features to help your team stay organized and productive
            </p>
          </div>
          
          <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-8">
            {features.map((feature) => {
              const Icon = feature.icon;
              return (
                <Card key={feature.title} className="p-6 hover:shadow-lg transition-shadow">
                  <div className="w-12 h-12 bg-indigo-100 dark:bg-indigo-900 rounded-lg flex items-center justify-center mb-4">
                    <Icon className="w-6 h-6 text-indigo-600 dark:text-indigo-400" />
                  </div>
                  <h3 className="text-xl mb-2 dark:text-white">{feature.title}</h3>
                  <p className="text-gray-600 dark:text-gray-400">{feature.description}</p>
                </Card>
              );
            })}
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="py-20 px-6">
        <div className="max-w-4xl mx-auto text-center">
          <h2 className="text-4xl mb-6 dark:text-white">Ready to get started?</h2>
          <p className="text-xl text-gray-600 dark:text-gray-400 mb-8">
            Join thousands of teams already using TaskFlow to build better products
          </p>
          <Link to="/register">
            <Button size="lg" className="text-lg">
              Create Your Free Account
              <ArrowRight className="w-5 h-5 ml-2" />
            </Button>
          </Link>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-gray-200 dark:border-gray-800 py-12 px-6">
        <div className="max-w-7xl mx-auto">
          <div className="grid md:grid-cols-4 gap-8">
            <div>
              <div className="flex items-center gap-2 mb-4">
                <div className="w-8 h-8 bg-indigo-600 rounded-lg flex items-center justify-center">
                  <LayoutGrid className="w-5 h-5 text-white" />
                </div>
                <span className="font-semibold dark:text-white">TaskFlow</span>
              </div>
              <p className="text-sm text-gray-600 dark:text-gray-400">
                Modern project management for agile teams
              </p>
            </div>
            
            <div>
              <h4 className="mb-4 dark:text-white">Product</h4>
              <ul className="space-y-2 text-sm text-gray-600 dark:text-gray-400">
                <li><a href="#" className="hover:text-indigo-600">Features</a></li>
                <li><a href="#" className="hover:text-indigo-600">Pricing</a></li>
                <li><a href="#" className="hover:text-indigo-600">Integrations</a></li>
              </ul>
            </div>
            
            <div>
              <h4 className="mb-4 dark:text-white">Company</h4>
              <ul className="space-y-2 text-sm text-gray-600 dark:text-gray-400">
                <li><a href="#" className="hover:text-indigo-600">About</a></li>
                <li><a href="#" className="hover:text-indigo-600">Blog</a></li>
                <li><a href="#" className="hover:text-indigo-600">Careers</a></li>
              </ul>
            </div>
            
            <div>
              <h4 className="mb-4 dark:text-white">Support</h4>
              <ul className="space-y-2 text-sm text-gray-600 dark:text-gray-400">
                <li><a href="#" className="hover:text-indigo-600">Help Center</a></li>
                <li><a href="#" className="hover:text-indigo-600">Contact</a></li>
                <li><a href="#" className="hover:text-indigo-600">Status</a></li>
              </ul>
            </div>
          </div>
          
          <div className="border-t border-gray-200 dark:border-gray-800 mt-8 pt-8 text-center text-sm text-gray-600 dark:text-gray-400">
            © 2026 TaskFlow. All rights reserved.
          </div>
        </div>
      </footer>
    </div>
  );
}
