# play-elasticsearch [![Build Status](https://travis-ci.org/Enalmada/play-elasticsearch.svg?branch=master)](https://travis-ci.org/Enalmada/play-elasticsearch) [![Join the chat at https://gitter.im/Enalmada/play-elasticsearch](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/Enalmada/play-elasticsearch?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.enalmada/play-elasticsearch/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.enalmada/play-elasticsearch)

[![Join the chat at https://gitter.im/Enalmada/play-elasticsearch](https://badges.gitter.im/Enalmada/play-elasticsearch.svg)](https://gitter.im/Enalmada/play-elasticsearch?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Play/Scala HTTP ElasticSearch interface.
This is initially based on the generic scala elasticsearch http client Wabisabi (https://github.com/gphat/wabisabi) so due credit goes there.  I wanted replace some dependencies unnecessary with play, add sample project, and then build on both from there.  Amazon hosts ElasticSearch but it only supports http interface so this seemed like the best route to go.  

#### Version information
* `2.4.0` to `2.4.x` (last: `0.1.5` - [master branch](https://github.com/enalmada/play-elasticsearch/tree/master))

Releases are on [mvnrepository](http://mvnrepository.com/artifact/com.github.enalmada) and snapshots can be found on [sonatype](https://oss.sonatype.org/content/repositories/snapshots/com/github/enalmada).

## Quickstart
Clone the project. Run the unit tests, they use a local embedded version of ES.
Edit your application.conf with your elasticsearch endpoint (and keys if aws) and and run `sbt run` to see a sample application.

### Including the Dependencies

```xml
<dependency>
    <groupId>com.github.enalmada</groupId>
    <artifactId>play-elasticsearch_2.11</artifactId>
    <version>0.1.5</version>
</dependency>
```
or

```scala
val appDependencies = Seq(
  "com.github.enalmada" %% "play-elasticsearch" % "0.1.5"
)
```

## Features
* Most of the critical ElasticSearch http endpoints with unit tests hitting in memory ES
* Sample project shows examples of: syncing, searching, synonyms, filters, pagination, analyzers

## Versions
* **TRUNK** [not released in the repository, yet]
  * Fancy contributing something? :-)
* **0.1.5** [release on 2015-12-22]
  * Added optional AWS request signing (bug fixes)
* **0.1.2** [release on 2015-12-21]
  * Added index open and close to support synonyms change.    
* **0.1.1** [release on 2015-12-14]
  * Fixed mapping bug.  
* **0.1.0** [release on 2015-12-13]
  * Initial release.

## TODO (help out!)
* Implement some nice traits so entities extend search/sync capabilities
* Incorporate some sort of dao
* Implement more full featured indexing page with query/explain plan support, etc
* Figure out how best to reindex settings/mappings without downtime (AWS doesn't support close index)
  
## License

Copyright (c) 2015 Adam Lane

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this work except in compliance with the License. You may obtain a copy of the License in the LICENSE file, or at:

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
  
