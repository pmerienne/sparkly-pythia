package pythia.core

case class Instance(rawFeatures: Map[String, _], inputMapper: Option[Mapper] = None, outputMapper: Option[Mapper] = None) {

  def inputFeature(name: String): Feature[Any] = {
    val realName = inputMapper.get.featureName(name)
    Feature(rawFeatures.get(realName))
  }

  def inputFeature(name: String, value: Any): Instance = {
    val realName = inputMapper.get.featureName(name)
    copy(rawFeatures = rawFeatures + (realName -> value))
  }

  def inputFeatures[T](name: String): FeatureList = {
    val realNames = inputMapper.get.featuresNames(name)
    val values = realNames.map(realName => rawFeatures.get(realName))
    FeatureList(values.map(value => Feature(value)))
  }

  def inputFeatures(name: String, values: List[_]): Instance = {
    val realNames = inputMapper.get.featuresNames(name)
    val newFeatures = (realNames zip values).toMap
    copy(rawFeatures = rawFeatures ++ newFeatures)
  }

  def outputFeature(name: String): Feature[Any] = {
    val realName = outputMapper.get.featureName(name)
    Feature(rawFeatures.get(realName))
  }

  def outputFeature(name: String, value: Any): Instance = {
    val realName = outputMapper.get.featureName(name)
    copy(rawFeatures = rawFeatures + (realName -> value))
  }

  def outputFeatures(name: String, values:List[_]): Instance = {
    val realNames = outputMapper.get.featuresNames(name)
    val newFeatures = (realNames zip values).toMap
    copy(rawFeatures =rawFeatures ++ newFeatures)
  }

  def outputFeatures[T](name: String): FeatureList = {
    val realNames = outputMapper.get.featuresNames(name)
    val values = realNames.map(realName => rawFeatures.get(realName))
    FeatureList(values.map(value => Feature(value)))
  }

}

object Instance {
  def apply(features: (String, _)*): Instance = new Instance(features.toMap)
  def apply(outputMapper: Mapper, features: (String, _)*): Instance = new Instance(features.toMap, outputMapper = Some(outputMapper))
}