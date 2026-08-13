import { scan } from 'sonarqube-scanner';

import process from 'node:process';
import console from 'node:console';

//
async function sonarScan() {
  try {
    await scan({
      serverUrl: 'http://sonarqube.gssira.com:9000',
      token: process.env.SONAR_TOKEN,
      options: {
        'sonar.projectKey': 'portfolio',
        'sonar.projectName': 'Portfolio',
        'sonar.projectDescription': 'Description for Portfolio project...',
        'sonar.sources': 'src',
        'sonar.tests': 'tests',
        'sonar.javascript.file.suffixes': '.js,.jsx'
      },
    });
    console.log('SonarQube analysis completed successfully');
    process.exit(0);
  } catch (error ) {
    console.error('Error occurred:', error.message);
    console.error('Stack trace:', error.stack);
    process.exit(1);
  }
}

sonarScan();
